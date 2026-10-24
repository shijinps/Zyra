# Deploying Zyra Web Voice Interface to Cloudflare Pages

The `web/` directory contains the complete, standalone Zyra Web Voice interface. It connects to the LiveKit Voice pipeline and backend API with zero server-side build dependencies.

---

## Method 1: Cloudflare Pages Direct Upload (Fastest - 1 Minute)

1. Open your [Cloudflare Dashboard](https://dash.cloudflare.com/).
2. In the left sidebar, navigate to **Compute (Workers) > Workers & Pages**.
3. Click **Create** > select the **Pages** tab > click **Upload assets**.
4. Enter a project name (e.g. `zyra-web`).
5. Drag and drop the `web` folder from this repository.
6. Click **Deploy site**.
7. Once deployed, click **Custom domains** > **Set up a custom domain**.
8. Enter `zyra.zyco.me` and confirm. Cloudflare will automatically route DNS and provision the SSL certificate instantly!

---

## Method 2: Cloudflare Pages with GitHub (Automatic Git Deploys)

1. In Cloudflare Dashboard, navigate to **Compute (Workers) > Workers & Pages**.
2. Click **Create** > select the **Pages** tab > click **Connect to Git**.
3. Select the `shijinps/Zyra` repository.
4. Set the build settings:
   - **Framework preset**: None
   - **Build command**: *(leave blank)*
   - **Build output directory**: `web`
5. Click **Save and Deploy**.
6. Under **Custom domains**, add `zyra.zyco.me`.

---

## Method 3: Vercel

1. Import `shijinps/Zyra` on [Vercel](https://vercel.com).
2. Set **Root Directory** to `web`.
3. Click **Deploy**.
4. In Settings > Domains, add `zyra.zyco.me` and point your DNS CNAME to Vercel.

---

## Changing Backend Domains in the Future

The web interface has a **Single Source of Truth** for the backend API URL:
- Located at the top of the `<script>` tag in `web/index.html`:
  ```javascript
  const DEFAULT_BACKEND_URL = 'https://apizyra.shijin.in';
  ```
- Users can also override this URL directly in the web UI by clicking the **Settings (gear icon)** at the top right, without needing to edit any code or redeploy!
