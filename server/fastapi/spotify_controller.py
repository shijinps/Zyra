import os
import json
import base64
import time
import logging
import requests
from typing import Optional, Dict, Any

logger = logging.getLogger("ZyraSpotify")
logger.setLevel(logging.INFO)

SPOTIFY_TOKEN_PATH = os.path.join(os.path.dirname(__file__), "spotify_token.json")

class SpotifyController:
    def __init__(self):
        self.client_id = os.getenv("SPOTIFY_CLIENT_ID", "18afa2a734744db9a76fb0fbd2e188ed")
        self.client_secret = os.getenv("SPOTIFY_CLIENT_SECRET", "188304993d4b4ed5b98bfe401b3297f3")
        self.redirect_uri = os.getenv("SPOTIFY_REDIRECT_URI", "https://apizyra.shijin.in/spotify/callback")
        self.access_token: Optional[str] = None
        self.refresh_token: Optional[str] = None
        self.token_expiry: float = 0.0
        self._load_token()

    def _load_token(self):
        """Loads saved refresh token from disk or env."""
        env_refresh = os.getenv("SPOTIFY_REFRESH_TOKEN", "")
        if env_refresh:
            self.refresh_token = env_refresh

        if os.path.exists(SPOTIFY_TOKEN_PATH):
            try:
                with open(SPOTIFY_TOKEN_PATH, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self.refresh_token = data.get("refresh_token") or self.refresh_token
                    self.access_token = data.get("access_token")
                    self.token_expiry = data.get("token_expiry", 0.0)
            except Exception as e:
                logger.warning(f"Failed to load spotify_token.json: {e}")

    def _save_token(self):
        """Persists tokens to disk."""
        try:
            with open(SPOTIFY_TOKEN_PATH, "w", encoding="utf-8") as f:
                json.dump({
                    "refresh_token": self.refresh_token,
                    "access_token": self.access_token,
                    "token_expiry": self.token_expiry
                }, f, indent=2)
        except Exception as e:
            logger.warning(f"Failed to save spotify_token.json: {e}")

    def get_login_url(self) -> str:
        """Generates Spotify OAuth login URL."""
        scopes = [
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "playlist-read-private",
            "user-library-read"
        ]
        scope_str = "%20".join(scopes)
        return (
            f"https://accounts.spotify.com/authorize?"
            f"client_id={self.client_id}&"
            f"response_type=code&"
            f"redirect_uri={self.redirect_uri}&"
            f"scope={scope_str}&"
            f"show_dialog=true"
        )

    def exchange_code(self, code: str) -> Dict[str, Any]:
        """Exchanges authorization code for access and refresh tokens."""
        auth_header = base64.b64encode(f"{self.client_id}:{self.client_secret}".encode()).decode()
        headers = {
            "Authorization": f"Basic {auth_header}",
            "Content-Type": "application/x-www-form-urlencoded"
        }
        data = {
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": self.redirect_uri
        }
        resp = requests.post("https://accounts.spotify.com/api/token", headers=headers, data=data, timeout=10)
        if resp.status_code == 200:
            res_json = resp.json()
            self.access_token = res_json.get("access_token")
            self.refresh_token = res_json.get("refresh_token") or self.refresh_token
            expires_in = res_json.get("expires_in", 3600)
            self.token_expiry = time.time() + expires_in - 60
            self._save_token()
            logger.info("Spotify tokens acquired and saved successfully!")
            return {"status": "success", "message": "Spotify authorized successfully!"}
        logger.error(f"Failed to exchange Spotify code: {resp.text}")
        return {"status": "error", "error": resp.text}

    def _ensure_access_token(self) -> bool:
        """Refreshes the access token if expired or about to expire."""
        self._load_token()
        if not self.refresh_token:
            return False

        if self.access_token and time.time() < self.token_expiry:
            return True

        auth_header = base64.b64encode(f"{self.client_id}:{self.client_secret}".encode()).decode()
        headers = {
            "Authorization": f"Basic {auth_header}",
            "Content-Type": "application/x-www-form-urlencoded"
        }
        data = {
            "grant_type": "refresh_token",
            "refresh_token": self.refresh_token
        }
        try:
            resp = requests.post("https://accounts.spotify.com/api/token", headers=headers, data=data, timeout=10)
            if resp.status_code == 200:
                res_json = resp.json()
                self.access_token = res_json.get("access_token")
                expires_in = res_json.get("expires_in", 3600)
                self.token_expiry = time.time() + expires_in - 60
                if "refresh_token" in res_json:
                    self.refresh_token = res_json["refresh_token"]
                self._save_token()
                return True
            logger.error(f"Failed to refresh Spotify token: {resp.text}")
        except Exception as e:
            logger.error(f"Error refreshing Spotify token: {e}")
        return False

    def _api_request(self, method: str, endpoint: str, json_data: Any = None, params: Any = None) -> Any:
        if not self._ensure_access_token():
            return {"error": "Spotify not authorized. Please log in via https://apizyra.shijin.in/spotify/login"}

        headers = {
            "Authorization": f"Bearer {self.access_token}",
            "Content-Type": "application/json"
        }
        url = f"https://api.spotify.com/v1/{endpoint.lstrip('/')}"
        
        try:
            resp = requests.request(method, url, headers=headers, json=json_data, params=params, timeout=8)
            if resp.status_code in [200, 201]:
                return resp.json()
            elif resp.status_code == 204:
                return {"status": "success"}
            elif resp.status_code == 404:
                return {"error": "No active Spotify playback device found. Open Spotify on your phone or PC first."}
            else:
                try:
                    return resp.json()
                except Exception:
                    return {"error": f"HTTP {resp.status_code}: {resp.text}"}
        except Exception as e:
            return {"error": str(e)}

    def play_query(self, query: str) -> str:
        """Searches Spotify and starts playback of the matching track, playlist, or album."""
        cleaned = query.strip()
        if not cleaned:
            return self.resume()

        # 1. Search Spotify for track, playlist, artist, album
        search_res = self._api_request("GET", "search", params={
            "q": cleaned,
            "type": "track,playlist,album,artist",
            "limit": 1
        })

        if "error" in search_res:
            return f"Couldn't search Spotify: {search_res['error']}"

        track = None
        tracks = search_res.get("tracks", {}).get("items", [])
        playlists = search_res.get("playlists", {}).get("items", [])
        albums = search_res.get("albums", {}).get("items", [])

        payload = {}
        target_name = ""
        target_artist = ""

        # Prioritize track match, or playlist if requested
        if "playlist" in cleaned.lower() and playlists:
            p = playlists[0]
            payload = {"context_uri": p["uri"]}
            target_name = f"playlist '{p['name']}'"
        elif tracks:
            t = tracks[0]
            payload = {"uris": [t["uri"]]}
            target_name = f"'{t['name']}'"
            target_artist = f" by {t['artists'][0]['name']}" if t.get("artists") else ""
        elif albums:
            a = albums[0]
            payload = {"context_uri": a["uri"]}
            target_name = f"album '{a['name']}'"
        else:
            return f"I couldn't find any results for '{cleaned}' on Spotify, Shijin."

        # 2. Check for active device, transfer if needed
        dev_res = self._api_request("GET", "me/player/devices")
        devices = dev_res.get("devices", []) if isinstance(dev_res, dict) else []
        
        active_device = next((d for d in devices if d.get("is_active")), None)
        if not active_device and devices:
            # Transfer to first available device
            self._api_request("PUT", "me/player", json_data={"device_ids": [devices[0]["id"]], "play": True})

        # 3. Command playback
        play_res = self._api_request("PUT", "me/player/play", json_data=payload)
        if isinstance(play_res, dict) and "error" in play_res:
            return f"Found {target_name}{target_artist}, but couldn't start playback: {play_res['error']}"

        return f"Playing {target_name}{target_artist} on Spotify now, Shijin!"

    def pause(self) -> str:
        """Pauses Spotify playback."""
        res = self._api_request("PUT", "me/player/pause")
        if isinstance(res, dict) and "error" in res:
            return f"Couldn't pause Spotify: {res['error']}"
        return "Music paused, Shijin."

    def resume(self) -> str:
        """Resumes Spotify playback."""
        res = self._api_request("PUT", "me/player/play")
        if isinstance(res, dict) and "error" in res:
            return f"Couldn't resume Spotify: {res['error']}"
        return "Resuming your music, Shijin."

    def next_track(self) -> str:
        """Skips to the next track."""
        res = self._api_request("POST", "me/player/next")
        if isinstance(res, dict) and "error" in res:
            return f"Couldn't skip track: {res['error']}"
        time.sleep(0.5)
        return self.get_currently_playing()

    def previous_track(self) -> str:
        """Skips to the previous track."""
        res = self._api_request("POST", "me/player/previous")
        if isinstance(res, dict) and "error" in res:
            return f"Couldn't go to previous track: {res['error']}"
        time.sleep(0.5)
        return self.get_currently_playing()

    def get_currently_playing(self) -> str:
        """Returns details about the currently playing track."""
        res = self._api_request("GET", "me/player/currently-playing")
        if isinstance(res, dict) and "error" in res:
            return f"Couldn't check what's playing: {res['error']}"

        item = res.get("item") if isinstance(res, dict) else None
        if not item:
            return "Nothing is currently playing on Spotify, Shijin."

        name = item.get("name", "Unknown Track")
        artists = ", ".join(a["name"] for a in item.get("artists", []))
        return f"Currently playing '{name}' by {artists}."

    def set_volume(self, volume_percent: int) -> str:
        """Sets playback volume (0-100)."""
        vol = max(0, min(100, volume_percent))
        res = self._api_request("PUT", "me/player/volume", params={"volume_percent": vol})
        if isinstance(res, dict) and "error" in res:
            return f"Couldn't change volume: {res['error']}"
        return f"Spotify volume set to {vol}%."

spotify_controller = SpotifyController()
