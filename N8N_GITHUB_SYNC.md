# n8n GitHub Auto-Sync Setup

## 🔍 Why Workflows Aren't Auto-Importing

n8n doesn't automatically import workflows from GitHub by default. You need to set up one of these methods:

---

## ✅ Method 1: n8n Git Sync (Recommended)

n8n has a built-in Git sync feature for version control.

### Enable Git Sync in n8n

1. **SSH to your VPS:**
   ```bash
   ssh your-user@your-vps-ip
   ```

2. **Find your n8n container:**
   ```bash
   docker ps | grep n8n
   ```

3. **Access n8n container:**
   ```bash
   docker exec -it <n8n-container-name> /bin/sh
   ```

4. **Navigate to n8n directory:**
   ```bash
   cd /home/node/.n8n
   ```

5. **Initialize Git (if not already):**
   ```bash
   git init
   git remote add origin https://github.com/shijinps/Zyra.git
   ```

6. **Pull workflows from GitHub:**
   ```bash
   git fetch origin main
   git checkout origin/main -- server/n8n/*.json
   ```

7. **Copy to workflows directory:**
   ```bash
   cp server/n8n/*.json /home/node/.n8n/workflows/
   ```

8. **Restart n8n:**
   ```bash
   exit
   docker restart <n8n-container-name>
   ```

---

## ✅ Method 2: n8n Environment Variable (Auto-Pull)

Configure n8n to auto-pull from Git on startup.

### Update n8n Configuration in Coolify

1. Go to **Coolify Dashboard**
2. Navigate to your **n8n service**
3. Click **Environment Variables**
4. Add these variables:

   ```bash
   N8N_VERSION_NOTIFICATIONS_ENABLED=true
   N8N_TEMPLATES_ENABLED=true
   WORKFLOWS_FOLDER=/home/node/.n8n/workflows
   ```

5. **Save** and **Restart** n8n

### Create Auto-Pull Script

1. **SSH to VPS**
2. **Create sync script:**
   ```bash
   cat > /opt/n8n-sync.sh << 'EOF'
   #!/bin/bash
   # n8n GitHub Sync Script
   
   CONTAINER_NAME="n8n-with-postgres-and-worker"  # Update with your container name
   REPO_URL="https://github.com/shijinps/Zyra.git"
   
   echo "Syncing n8n workflows from GitHub..."
   
   # Pull latest from GitHub inside container
   docker exec $CONTAINER_NAME sh -c "
     cd /tmp &&
     rm -rf Zyra &&
     git clone $REPO_URL &&
     cp Zyra/server/n8n/*.json /home/node/.n8n/workflows/ &&
     chown -R node:node /home/node/.n8n/workflows/
   "
   
   echo "Restarting n8n..."
   docker restart $CONTAINER_NAME
   
   echo "Sync complete!"
   EOF
   ```

3. **Make executable:**
   ```bash
   chmod +x /opt/n8n-sync.sh
   ```

4. **Run manually:**
   ```bash
   /opt/n8n-sync.sh
   ```

5. **Set up cron for auto-sync (optional):**
   ```bash
   crontab -e
   ```
   
   Add this line to sync every hour:
   ```
   0 * * * * /opt/n8n-sync.sh >> /var/log/n8n-sync.log 2>&1
   ```

---

## ✅ Method 3: GitHub Webhook (Real-time Sync)

Set up a webhook to auto-sync on every push.

### Create n8n Webhook Workflow

1. In n8n, create a new workflow: **"GitHub Sync"**

2. Add **Webhook** node:
   - Path: `github-sync`
   - Method: `POST`

3. Add **Execute Command** node:
   ```bash
   cd /tmp &&
   rm -rf Zyra &&
   git clone https://github.com/shijinps/Zyra.git &&
   cp Zyra/server/n8n/*.json /home/node/.n8n/workflows/ &&
   chown -R node:node /home/node/.n8n/workflows/
   ```

4. **Activate** the workflow

5. Copy the webhook URL (e.g., `https://n8n.shijin.in/webhook/github-sync`)

### Configure GitHub Webhook

1. Go to **GitHub** → Your **Zyra repository**
2. Click **Settings** → **Webhooks** → **Add webhook**
3. Configure:
   - **Payload URL:** `https://n8n.shijin.in/webhook/github-sync`
   - **Content type:** `application/json`
   - **Secret:** (optional, for security)
   - **Events:** Select "Just the push event"
4. Click **Add webhook**

Now, every time you push to GitHub, n8n will auto-sync!

---

## 🚀 Quick Fix: Manual Import for Now

Since auto-sync isn't set up yet, here's the quickest way to import:

### Option A: Via n8n UI (2 minutes)

1. Open n8n: `https://n8n.shijin.in`
2. Click **"Create workflow"**
3. Click **⋮** → **"Import from URL"**
4. Paste:
   ```
   https://raw.githubusercontent.com/shijinps/Zyra/main/server/n8n/spatial_intelligence_workflow.json
   ```
5. Click **"Import"**
6. Done! ✅

### Option B: Via SSH (5 minutes)

```bash
# SSH to VPS
ssh your-user@your-vps-ip

# Find n8n container
docker ps | grep n8n

# Access container
docker exec -it <container-name> /bin/sh

# Pull from GitHub
cd /tmp
git clone https://github.com/shijinps/Zyra.git
cp Zyra/server/n8n/*.json /home/node/.n8n/workflows/
chown -R node:node /home/node/.n8n/workflows/

# Exit and restart
exit
docker restart <container-name>
```

---

## 📊 Verify Import

1. Open n8n web interface
2. Go to **Workflows**
3. You should see:
   - ✅ Zyra MCP Tool Bridge
   - ✅ Zyra Proactive Incident Alert
   - ✅ **Zyra Spatial Intelligence** (new!)

---

## 🔧 Recommended Setup

For your use case, I recommend:

1. **Now:** Use **Option A** (Import from URL) - fastest
2. **Later:** Set up **Method 3** (GitHub Webhook) - automatic on every push

This way:
- ✅ Workflows auto-update when you push to GitHub
- ✅ No manual sync needed
- ✅ Real-time deployment

---

## ✅ Checklist

- [ ] Import spatial_intelligence_workflow.json (via URL or SSH)
- [ ] Verify workflow appears in n8n
- [ ] Set up GitHub webhook (optional, for auto-sync)
- [ ] Test workflow execution

---

## 🎉 Quick Action

**Right now, the fastest way:**

1. Open n8n
2. Create workflow → Import from URL
3. Paste: `https://raw.githubusercontent.com/shijinps/Zyra/main/server/n8n/spatial_intelligence_workflow.json`
4. Configure credentials (Groq + ElevenLabs)
5. Activate!

Then set up the GitHub webhook for future auto-sync! 🚀
