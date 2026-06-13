import discord
from discord.ext import commands, tasks
from dotenv import load_dotenv
import json, os, datetime, sqlite3, string, random, logging, asyncio
import hmac as hmac_lib, hashlib
import aiohttp
from pathlib import Path

load_dotenv()

TOKEN = os.getenv("LUMINA_BOT_TOKEN", "")
GUILD_ID = int(os.getenv("LUMINA_GUILD_ID", "0"))
CONFIG_FILE = "ultimate_config.json"
DB_FILE = "lumina_bot.db"
LUMINA_COLOR = discord.Color.from_rgb(106, 13, 173)
LAUNCHER_DATA = Path.home() / ".luminamc" / "instances"

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

intents = discord.Intents.all()
bot = commands.Bot(command_prefix="!", intents=intents, help_command=None)
tasks_started = False  # Guard to prevent tasks starting multiple times
spam_tracker = {}  # user_id -> list of (timestamp, content) for anti-spam

def init_db():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS vip_bonuses (
        discord_id INTEGER PRIMARY KEY,
        last_bonus_date TEXT
    )''')
    conn.commit()
    conn.close()

DEFAULT_CONFIG = {
    "admin_role_id": None, "mod_role_id": None, "vip_role_id": None,
    "member_role_id": None, "bot_role_id": None, "veteran_role_id": None,
    "level5_role_id": None, "level10_role_id": None, "level25_role_id": None,
    "ping_updates_role_id": None, "ping_events_role_id": None,
    "status_channel_id": None, "announcements_channel_id": None, "suggestions_channel_id": None,
    "tickets_channel_id": None, "vip_channel_id": None, "giveaways_channel_id": None,
    "mod_logs_channel_id": None, "getroles_channel_id": None, "botcmds_channel_id": None,
    "leveling_channel_id": None, "leaderboard_channel_id": None, "stats_channel_id": None,
    "warnings_channel_id": None,
    "temp_vc_category_id": None,
    "open_tickets": {}, "warnings": {}, "leveling": {},
    "server_stats": {"total_warnings": 0, "total_mutes": 0},
    "launcher_version": "1.0.0", "launcher_status": "online",
    "share_channels": {},
    "last_github_release": "v0.0.0",
    "last_bot_start": "",
    "vip_codes": {},
    "giveaways": {},
    "temp_vcs": {}
}

def load_config():
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE) as f:
            cfg = json.load(f)
            for k, v in DEFAULT_CONFIG.items():
                if k not in cfg:
                    cfg[k] = v
            return cfg
    return DEFAULT_CONFIG.copy()

def save_config(cfg):
    with open(CONFIG_FILE, "w") as f:
        json.dump(cfg, f, indent=2, default=str)

async def is_admin(user: discord.Member) -> bool:
    if user.guild_permissions.administrator:
        return True
    cfg = load_config()
    rid = cfg.get("admin_role_id")
    if rid:
        role = user.guild.get_role(rid)
        return role in user.roles if role else False
    return False

async def is_mod(user: discord.Member) -> bool:
    if await is_admin(user): return True
    cfg = load_config()
    rid = cfg.get("mod_role_id")
    if rid:
        role = user.guild.get_role(rid)
        return role in user.roles if role else False
    return user.guild_permissions.moderate_members

def get_instances():
    instances = {}
    if not LAUNCHER_DATA.exists():
        return instances
    for d in LAUNCHER_DATA.iterdir():
        if d.is_dir():
            fp = d / "instance.json"
            if fp.exists():
                try:
                    with open(fp) as f:
                        instances[d.name] = json.load(f)
                except: pass
    return instances

async def log_action(guild, title, user=None, desc="", color=LUMINA_COLOR):
    cfg = load_config()
    cid = cfg.get("mod_logs_channel_id")
    if not cid: return
    ch = guild.get_channel(cid)
    if not ch: return
    embed = discord.Embed(title=title, description=desc, color=color, timestamp=datetime.datetime.now())
    if user:
        embed.set_author(name=str(user), icon_url=user.display_avatar.url)
    try:
        await ch.send(embed=embed)
    except: pass

GITHUB_REPO = "Squexso/Lumina"

# Built-in changelogs for versions where the GitHub release body is sparse.
# Keyed by exact tag string (e.g. "v0.1.4").
RELEASE_CHANGELOGS = {
    "v0.1.4": {
        "new": [
            "VIP Shop — buy the 👑 Supernova Discord role for 15,000 Lumina Tokens",
            "Smart crash analyzer — shows what went wrong and exactly how to fix it",
            "Client import tool — safely copy saves, mods, configs from any Minecraft client",
            "Daily login streak + Creator Codes in the Shop",
        ],
        "fixed": [
            "Performance mod installer now detects Lithium so it no longer double-installs",
            "LambDynamicLights only installs when the performance-mod toggle is on",
            "Discord bot was showing launcher version 1.3 instead of 0.1.4",
        ],
        "improved": [
            "Crash reports now show a human-readable cause + fix suggestion instead of raw logs",
            "Shop UI — VIP card with gold glow at the top of the Cosmetics tab",
        ],
    },
    "v0.1.5": {
        "new": [
            "VIP Shop — buy the 👑 Supernova Discord role for 15,000 Lumina Tokens",
            "/redeem-vip Discord command — paste your code to instantly get the Supernova role",
            "Smart crash analyzer — plain-language cause + fix for every crash type",
            "Client import tool — safely copy saves, mods and configs from any other client",
        ],
        "fixed": [
            "Performance mod installer no longer double-installs when Lithium is already present",
            "LambDynamicLights now only installs when the performance-mod feature is enabled",
            "Discord bot was displaying launcher version as 1.3 — corrected to 0.1.4/0.1.5",
        ],
        "improved": [
            "Crash reports show a human-readable diagnosis instead of raw log lines",
            "VIP card shows step-by-step instructions and a warning not to share the code",
            "Discord changelog now shows real New / Fixed / Improved sections per version",
        ],
    },
}

async def fetch_latest_release():
    """Return dict {tag, url, body} of the latest GitHub release, or None."""
    try:
        async with aiohttp.ClientSession() as session:
            url = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"
            async with session.get(url, headers={"Accept": "application/vnd.github+json"}) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    return {
                        "tag": data.get("tag_name", "?"),
                        "url": data.get("html_url", f"https://github.com/{GITHUB_REPO}/releases"),
                        "body": (data.get("body") or "No release notes.")[:1000],
                        "name": data.get("name") or data.get("tag_name", "Release"),
                    }
    except: pass
    return None

def build_leaderboard_embed(guild, cfg):
    lv = cfg.get("leveling", {})
    ranked = sorted(lv.items(), key=lambda x: (x[1].get("level", 1), x[1].get("xp", 0)), reverse=True)[:10]
    e = discord.Embed(title="🏆 XP Leaderboard", color=LUMINA_COLOR, timestamp=datetime.datetime.now())
    if not ranked:
        e.description = "No data yet — start chatting to earn XP! 🎮"
    else:
        medals = ["🥇", "🥈", "🥉"]
        lines = []
        for i, (uid, d) in enumerate(ranked):
            m = guild.get_member(int(uid))
            name = m.display_name if m else f"User {uid}"
            tag = medals[i] if i < 3 else f"**{i+1}.**"
            lines.append(f"{tag} {name} — Level **{d.get('level',1)}** ({d.get('xp',0)} XP)")
        e.description = "\n".join(lines)
    e.set_footer(text="Updates every 12 hours")
    return e

def build_status_embed(guild, cfg):
    e = discord.Embed(title="🔴 Launcher Status", color=LUMINA_COLOR, timestamp=datetime.datetime.now())
    e.add_field(name="🚀 Launcher", value=f"✅ {cfg.get('launcher_status','online').title()}", inline=True)
    e.add_field(name="📦 Version", value=f"v{cfg.get('launcher_version','?')}", inline=True)
    e.add_field(name="🤖 Bot Ping", value=f"{round(bot.latency*1000)}ms", inline=True)
    e.set_footer(text="Updates every 2 minutes")
    return e

def build_serverstats_embed(guild, cfg):
    member_count = guild.member_count - len([m for m in guild.members if m.bot])
    bots = len([m for m in guild.members if m.bot])
    vip = 0
    if cfg.get("vip_role_id"):
        r = guild.get_role(cfg["vip_role_id"])
        if r: vip = len(r.members)
    e = discord.Embed(title="📊 Server Statistics", color=LUMINA_COLOR, timestamp=datetime.datetime.now())
    e.add_field(name="👥 Members", value=f"**{member_count}**", inline=True)
    e.add_field(name="🤖 Bots", value=f"**{bots}**", inline=True)
    e.add_field(name="👑 VIPs", value=f"**{vip}**", inline=True)
    e.add_field(name="💬 Channels", value=f"**{len(guild.text_channels)}**", inline=True)
    e.add_field(name="⚠️ Warnings", value=f"**{cfg['server_stats'].get('total_warnings',0)}**", inline=True)
    e.add_field(name="🎫 Tickets", value=f"**{len(cfg.get('open_tickets',{}))}**", inline=True)
    e.set_footer(text="Updates every 2 minutes")
    return e

def clean_notes(body):
    """Parse GitHub markdown release notes into clean Discord text (strips URLs/Full Changelog)."""
    lines = []
    for ln in (body or "").split("\n"):
        low = ln.lower()
        if "http://" in low or "https://" in low or "full changelog" in low or "www." in low:
            continue
        # Convert ## headings to bold
        stripped = ln.lstrip("#").strip()
        if ln.startswith("#") and stripped:
            ln = f"**{stripped}**"
        # Convert markdown bullets to Discord bullets
        elif ln.startswith(("- ", "* ")):
            ln = "• " + ln[2:]
        lines.append(ln)
    return "\n".join(lines).strip() or None

def build_update_embed(rel):
    """launcher-updates → version + download + top highlights."""
    e = discord.Embed(
        title=f"🚀 {rel['name']}",
        description="A new launcher version is available! Download it from GitHub.",
        color=LUMINA_COLOR,
        url=rel["url"]
    )
    e.add_field(name="📦 Version", value=f"`{rel['tag']}`", inline=True)
    e.add_field(name="🔗 Download", value=f"[GitHub Release]({rel['url']})", inline=True)
    cl = RELEASE_CHANGELOGS.get(rel["tag"])
    if cl and cl.get("new"):
        preview = "\n".join(f"• {x}" for x in cl["new"][:3])
        e.add_field(name="✨ Highlights", value=preview, inline=False)
    e.set_footer(text=f"LuminaMC Launcher · {rel['tag']}")
    return e

def build_changelog_embed(rel):
    """changelog → rich sections: New / Fixed / Improved."""
    tag = rel["tag"]
    e = discord.Embed(title=f"📋 {tag} — What's New", color=LUMINA_COLOR, timestamp=datetime.datetime.now())
    cl = RELEASE_CHANGELOGS.get(tag)
    if cl:
        if cl.get("new"):
            e.add_field(name="✨ New", value="\n".join(f"• {x}" for x in cl["new"]), inline=False)
        if cl.get("fixed"):
            e.add_field(name="🐛 Fixed", value="\n".join(f"• {x}" for x in cl["fixed"]), inline=False)
        if cl.get("improved"):
            e.add_field(name="🔧 Improved", value="\n".join(f"• {x}" for x in cl["improved"]), inline=False)
    else:
        notes = clean_notes(rel["body"])
        e.description = notes or "✨ General improvements, fixes and polish."
    e.set_footer(text=f"LuminaMC Launcher · {tag}")
    return e

async def edit_or_send(ch, embed):
    """Edit the bot's existing embed in the channel, or send a new pinned one."""
    if not ch: return
    try:
        async for msg in ch.history(limit=8, oldest_first=True):
            if msg.author == ch.guild.me and msg.embeds:
                await msg.edit(embed=embed)
                return
        msg = await ch.send(embed=embed)
        try: await msg.pin()
        except: pass
    except: pass

def add_xp(user_id: int, xp_amount: int = 10):
    cfg = load_config()
    uid = str(user_id)
    if uid not in cfg["leveling"]:
        cfg["leveling"][uid] = {"level": 1, "xp": 0, "messages": 0}

    cfg["leveling"][uid]["xp"] += xp_amount
    cfg["leveling"][uid]["messages"] += 1

    lvl = cfg["leveling"][uid]["level"]
    xp = cfg["leveling"][uid]["xp"]
    xp_needed = lvl * 100

    new_level = lvl
    leveled_up = False

    while xp >= xp_needed:
        xp -= xp_needed
        new_level += 1
        xp_needed = new_level * 100
        leveled_up = True

    cfg["leveling"][uid]["level"] = new_level
    cfg["leveling"][uid]["xp"] = xp
    save_config(cfg)

    return leveled_up, new_level

async def handle_levelup(member, new_level):
    cfg = load_config()
    guild = member.guild

    # Assign level roles at thresholds (5 / 10 / 25)
    thresholds = [(25, "level25_role_id"), (10, "level10_role_id"), (5, "level5_role_id")]
    earned = None
    for lvl, key in thresholds:
        if new_level >= lvl and cfg.get(key):
            role = guild.get_role(cfg[key])
            if role and role not in member.roles:
                try:
                    await member.add_roles(role)
                    earned = role
                except: pass
            break

    # Announce in the leveling channel (fallback: where they typed)
    ch = guild.get_channel(cfg["leveling_channel_id"]) if cfg.get("leveling_channel_id") else None
    if not ch:
        ch = next((c for c in guild.text_channels if "leveling" in c.name.lower()), None)
    if ch:
        e = discord.Embed(title="🎉 Level Up!", description=f"{member.mention} reached **Level {new_level}**! 🚀", color=LUMINA_COLOR)
        e.set_thumbnail(url=member.display_avatar.url)
        if earned:
            e.add_field(name="🏅 New Role", value=earned.mention, inline=False)
        try: await ch.send(embed=e)
        except: pass

@bot.event
async def on_ready():
    global tasks_started
    logger.info(f"✅ {bot.user} is online!")
    init_db()

    # Prevent multiple bot instances
    cfg = load_config()
    now = datetime.datetime.now()
    last_start = cfg.get("last_bot_start", "")
    if last_start:
        try:
            last_start_time = datetime.datetime.fromisoformat(last_start)
            time_since = (now - last_start_time).total_seconds()
            if time_since < 10:
                logger.error(f"❌ Another bot instance running (started {time_since:.0f}s ago). Shutting down to prevent duplicates...")
                await bot.close()
                return
        except: pass

    cfg["last_bot_start"] = now.isoformat()
    save_config(cfg)

    try:
        synced = await bot.tree.sync()
        logger.info(f"✅ {len(synced)} commands synced")
    except Exception as e:
        logger.error(f"Sync error: {e}")

    await bot.change_presence(activity=discord.Activity(
        type=discord.ActivityType.watching, name="Lumina MC | /help"))

    if not tasks_started:
        if not auto_tasks.is_running():
            auto_tasks.start()
        if not update_status_channels.is_running():
            update_status_channels.start()
        if not cleanup_share_channels.is_running():
            cleanup_share_channels.start()
        if not check_github_releases.is_running():
            check_github_releases.start()
        if not check_giveaways.is_running():
            check_giveaways.start()
        if not update_leaderboard.is_running():
            update_leaderboard.start()
        tasks_started = True

@bot.event
async def on_member_join(member: discord.Member):
    cfg = load_config()
    if member.bot:
        rid = cfg.get("bot_role_id")
        if rid:
            r = member.guild.get_role(rid)
            if r:
                try: await member.add_roles(r)
                except: pass
        return

    rid = cfg.get("member_role_id")
    if rid:
        r = member.guild.get_role(rid)
        if r:
            try: await member.add_roles(r)
            except: pass

@bot.event
async def on_voice_state_update(member, before, after):
    if member.bot:
        return
    cfg = load_config()
    guild = member.guild

    # User joined "➕ Join to Create" → make them a private room
    if after.channel and after.channel.name == "➕ Join to Create":
        cat = after.channel.category
        # Owner can join/speak but NOT edit the channel — control is via the bot buttons only
        overwrites = {
            guild.default_role: discord.PermissionOverwrite(view_channel=True, connect=True),
            member: discord.PermissionOverwrite(view_channel=True, connect=True, speak=True),
            guild.me: discord.PermissionOverwrite(view_channel=True, connect=True, manage_channels=True, move_members=True),
        }
        try:
            vc = await guild.create_voice_channel(name=f"🔊 {member.display_name}'s Room", category=cat, overwrites=overwrites, user_limit=0)
            await member.move_to(vc)
            cfg["temp_vcs"][str(vc.id)] = member.id
            save_config(cfg)

            e = discord.Embed(title="🔊 Your Private Room", description=f"{member.mention}, this room is yours! 🎉\n\n**Choose how many people can join (1–10):**", color=LUMINA_COLOR)
            view = discord.ui.View(timeout=None)
            # Numbers 1–10 across two rows
            for n in range(1, 11):
                view.add_item(discord.ui.Button(label=f"{n}", style=discord.ButtonStyle.blurple, custom_id=f"vclimit_{vc.id}_{n}", row=0 if n <= 5 else 1))
            view.add_item(discord.ui.Button(label="∞ No limit", style=discord.ButtonStyle.grey, custom_id=f"vclimit_{vc.id}_0", row=2))
            view.add_item(discord.ui.Button(label="🔒 Lock", style=discord.ButtonStyle.red, custom_id=f"vclock_{vc.id}", row=2))
            view.add_item(discord.ui.Button(label="🔓 Unlock", style=discord.ButtonStyle.green, custom_id=f"vcunlock_{vc.id}", row=2))
            try: await vc.send(embed=e, view=view)
            except: pass
        except: pass

    # User left a temp room → delete it if now empty
    if before.channel and str(before.channel.id) in cfg.get("temp_vcs", {}):
        if len(before.channel.members) == 0:
            try: await before.channel.delete(reason="Temp room empty")
            except: pass
            cfg["temp_vcs"].pop(str(before.channel.id), None)
            save_config(cfg)

@bot.event
async def on_message(message: discord.Message):
    # Auto-delete pin notifications
    if message.type == discord.MessageType.pins_add:
        try:
            await message.delete()
        except: pass
        return

    if message.author.bot:
        return

    leveled_up, new_level = add_xp(message.author.id, 10)
    if leveled_up and isinstance(message.author, discord.Member):
        await handle_levelup(message.author, new_level)

    # ── Anti-Spam ──  (mods/admins are exempt)
    if isinstance(message.author, discord.Member) and not await is_mod(message.author):
        now = datetime.datetime.now().timestamp()
        uid = message.author.id
        history = [t for t in spam_tracker.get(uid, []) if now - t[0] < 10]  # last 10s
        history.append((now, message.content.lower().strip()))
        spam_tracker[uid] = history

        recent = [t for t in history if now - t[0] < 7]
        same = [t for t in history if t[1] == message.content.lower().strip() and message.content.strip()]

        reason = None
        if len(recent) >= 5:
            reason = "Sending messages too fast (5+ in 7s)"
        elif len(same) >= 3:
            reason = "Repeating the same message"
        elif len(message.content) > 8 and sum(1 for c in message.content if c.isupper()) / len(message.content) > 0.7:
            reason = "Too many CAPS"

        if reason:
            spam_tracker[uid] = []  # reset so we don't double-punish
            await handle_spam(message, reason)
            return

    await bot.process_commands(message)

async def handle_spam(message, reason):
    user = message.author
    guild = message.guild

    # Delete the user's recent messages in this channel
    try:
        await message.channel.purge(limit=6, check=lambda m: m.author.id == user.id)
    except: pass

    # Short timeout
    try:
        await user.timeout(datetime.timedelta(minutes=5), reason=f"Spam: {reason}")
    except: pass

    # Count the warning
    cfg = load_config()
    cfg["server_stats"]["total_warnings"] = cfg["server_stats"].get("total_warnings", 0) + 1
    wkey = str(user.id)
    cfg["warnings"].setdefault(wkey, []).append({"reason": f"Auto: {reason}", "by": "AntiSpam", "date": datetime.datetime.now().isoformat()})
    save_config(cfg)
    count = len(cfg["warnings"][wkey])

    # Tell the channel briefly
    try:
        await message.channel.send(f"🚫 {user.mention} stop spamming! (muted 5m)", delete_after=6)
    except: pass

    # Log to the warnings channel
    wch = guild.get_channel(cfg.get("warnings_channel_id")) if cfg.get("warnings_channel_id") else None
    if not wch:
        wch = next((c for c in guild.text_channels if "warning" in c.name.lower()), None)
    if wch:
        e = discord.Embed(title="🚫 Spam Detected", color=discord.Color.red(), timestamp=datetime.datetime.now())
        e.set_author(name=str(user), icon_url=user.display_avatar.url)
        e.add_field(name="👤 User", value=user.mention, inline=True)
        e.add_field(name="📍 Channel", value=message.channel.mention, inline=True)
        e.add_field(name="⚠️ Reason", value=reason, inline=False)
        e.add_field(name="🔇 Action", value="Muted 5 min + messages deleted", inline=True)
        e.add_field(name="📊 Total Warnings", value=str(count), inline=True)
        try: await wch.send(embed=e)
        except: pass

@bot.event
async def on_interaction(interaction: discord.Interaction):
    if interaction.type == discord.InteractionType.component:
        cid = interaction.data.get("custom_id", "")

        if cid == "open_ticket":
            cfg = load_config()
            guild = interaction.guild
            user = interaction.user

            # Private channel: only the user + staff + bot can see
            overwrites = {
                guild.default_role: discord.PermissionOverwrite(view_channel=False),
                user: discord.PermissionOverwrite(view_channel=True, send_messages=True, read_message_history=True),
                guild.me: discord.PermissionOverwrite(view_channel=True, send_messages=True),
            }
            for key in ("admin_role_id", "mod_role_id"):
                rid = cfg.get(key)
                if rid:
                    role = guild.get_role(rid)
                    if role:
                        overwrites[role] = discord.PermissionOverwrite(view_channel=True, send_messages=True, manage_messages=True)
            try:
                cat = interaction.channel.category
                ch = await guild.create_text_channel(f"ticket-{user.name[:12]}", category=cat, overwrites=overwrites)
                cfg["open_tickets"][str(ch.id)] = {"creator": user.id}
                save_config(cfg)

                embed = discord.Embed(title="🎫 Support Ticket", description=f"Hey {user.mention}! 👋\nDescribe your problem and staff will help you.\n\nClick **Close Ticket** when done.", color=LUMINA_COLOR)
                view = discord.ui.View()
                close_btn = discord.ui.Button(label="Close Ticket", style=discord.ButtonStyle.red, emoji="❌")
                close_btn.custom_id = "close_ticket"
                view.add_item(close_btn)
                await ch.send(embed=embed, view=view)
                await interaction.response.send_message(f"✅ Ticket created: {ch.mention}", ephemeral=True)
            except Exception as e:
                try: await interaction.response.send_message(f"❌ Error: {e}", ephemeral=True)
                except: pass

        elif "close_ticket" in cid:
            cfg = load_config()
            ch_id = str(interaction.channel.id)
            if ch_id in cfg["open_tickets"]:
                try:
                    await interaction.channel.delete(reason="Ticket closed")
                    del cfg["open_tickets"][ch_id]
                    save_config(cfg)
                except Exception as e:
                    await interaction.response.send_message(f"❌ Error: {e}", ephemeral=True)

        elif cid.startswith("giveaway_"):
            gid = cid.replace("giveaway_", "")
            cfg = load_config()
            gw = cfg["giveaways"].get(gid)
            if not gw:
                await interaction.response.send_message("❌ Giveaway not found!", ephemeral=True)
                return
            uid = interaction.user.id
            if uid in gw["participants"]:
                await interaction.response.send_message("✅ You already joined!", ephemeral=True)
                return
            gw["participants"].append(uid)
            save_config(cfg)
            count = len(gw["participants"])

            # Update the giveaway embed's Participants field live
            try:
                msg = interaction.message
                if msg and msg.embeds:
                    emb = msg.embeds[0]
                    for i, field in enumerate(emb.fields):
                        if "Participants" in field.name:
                            emb.set_field_at(i, name=field.name, value=str(count), inline=field.inline)
                            break
                    await msg.edit(embed=emb)
            except: pass

            await interaction.response.send_message(f"🎁 You joined the giveaway! Total: {count}", ephemeral=True)

        elif cid in ("role_updates", "role_events"):
            cfg = load_config()
            key = "ping_updates_role_id" if cid == "role_updates" else "ping_events_role_id"
            label = "📢 Update Notifications" if cid == "role_updates" else "🎉 Event Notifications"
            rid = cfg.get(key)
            role = interaction.guild.get_role(rid) if rid else None
            if not role:
                await interaction.response.send_message("❌ Role not found! Ask an admin to run /setup-server.", ephemeral=True)
                return
            member = interaction.user
            try:
                if role in member.roles:
                    await member.remove_roles(role)
                    await interaction.response.send_message(f"➖ Removed **{label}**", ephemeral=True)
                else:
                    await member.add_roles(role)
                    await interaction.response.send_message(f"➕ You now have **{label}**", ephemeral=True)
            except Exception as e:
                await interaction.response.send_message(f"❌ Error: {e}", ephemeral=True)

        elif cid.startswith("vclimit_") or cid.startswith("vclock_") or cid.startswith("vcunlock_"):
            cfg = load_config()
            parts = cid.split("_")
            vid = parts[1]
            # Only the room owner may control it
            if cfg.get("temp_vcs", {}).get(vid) != interaction.user.id:
                await interaction.response.send_message("❌ Only the room owner can change this!", ephemeral=True)
                return
            vc = interaction.guild.get_channel(int(vid))
            if not vc:
                await interaction.response.send_message("❌ Room not found!", ephemeral=True)
                return
            try:
                if cid.startswith("vclimit_"):
                    n = int(parts[2])
                    await vc.edit(user_limit=n)
                    await interaction.response.send_message(f"✅ Limit set to {'∞ (no limit)' if n == 0 else f'{n} people'}", ephemeral=True)
                elif cid.startswith("vclock_"):
                    await vc.set_permissions(interaction.guild.default_role, connect=False)
                    await interaction.response.send_message("🔒 Room locked — nobody new can join!", ephemeral=True)
                else:
                    await vc.set_permissions(interaction.guild.default_role, connect=True)
                    await interaction.response.send_message("🔓 Room unlocked — anyone can join!", ephemeral=True)
            except Exception as e:
                await interaction.response.send_message(f"❌ Error: {e}", ephemeral=True)

@bot.tree.command(name="help", description="Show available commands")
async def help_cmd(interaction: discord.Interaction):
    e = discord.Embed(title="📖 Lumina Bot — Help", description="Available commands:", color=LUMINA_COLOR)
    e.add_field(name="💬 Chat", value="`/ask` — Chat\n`/ticket` — Support\n`/suggest` — Suggestion", inline=False)
    e.add_field(name="📦 Share", value="`/share-setup` — Share mods\n`/invite-friend` — Invite", inline=False)
    e.add_field(name="📊 Stats", value="`/level` — Your level\n`/leaderboard` — Top members\n`/member-stats` — Server stats\n`/status` — Bot status\n`/show-instance` — Instance mods", inline=False)
    await interaction.response.send_message(embed=e, ephemeral=True)

@bot.tree.command(name="ask", description="Chat with the bot")
async def ask(interaction: discord.Interaction, question: str):
    await interaction.response.defer()
    q = question.lower()

    # Keyword-based answers — checked in order, first match wins
    # Each entry: (list of keywords, answer)
    qa = [
        (["install mod", "add mod", "how do i install", "mods install", "install a mod"],
         "📦 **How to install mods:**\n1. Open the Lumina Launcher\n2. Select your instance\n3. Click **Mods** → **Add Mod**\n4. Search & install, or drop a `.jar` into the mods folder\n5. Launch and enjoy! 🎮"),
        (["update", "new version", "latest"],
         "🚀 The launcher auto-checks for updates! Open it and it'll prompt you if a new version is out."),
        (["vip", "premium"],
         "💜 VIP gives you perks! Buy it in the launcher and use `/add-vip` to get your role."),
        (["crash", "error", "not working", "won't start", "wont start"],
         "🛠️ Try: 1) Update the launcher, 2) Allocate more RAM in settings, 3) Check your mods are compatible. Still stuck? Open a `/ticket`!"),
        (["ram", "memory", "lag", "fps"],
         "⚙️ Boost performance: increase RAM in launcher settings, and install **Sodium** + **Lithium** mods for more FPS!"),
        (["share", "modpack", "friend"],
         "📦 Use `/share-setup` to share your mods with friends in a private channel!"),
        (["server", "join", "ip"],
         "🌐 Check the server list in the launcher's **Servers** tab to join!"),
        (["hi", "hello", "hey", "yo"], "Hey there! 👋 How can I help?"),
        (["how are you"], "I'm doing great! 🎮 Ready to help you with Lumina!"),
        (["help", "what can you do"], "I'm here to help! 💪 Try `/help` to see all commands."),
        (["launcher"], "🚀 Lumina Launcher is awesome! Modpacks, capes, servers — all in one place."),
        (["thank", "thx", "danke"], "You're welcome! 😊"),
    ]

    answer = None
    for keywords, response in qa:
        if any(k in q for k in keywords):
            answer = response
            break

    if not answer:
        answer = "🤔 I'm not sure about that one! Try `/help` for commands, or open a `/ticket` to ask a human."

    e = discord.Embed(title="💬 Lumina Help", description=answer, color=LUMINA_COLOR)
    e.set_footer(text=f"You asked: {question[:100]}")
    await interaction.followup.send(embed=e)

@bot.tree.command(name="share-setup", description="Share mods with friends")
async def share_setup(interaction: discord.Interaction, mods: str = ""):
    await interaction.response.defer()
    guild = interaction.guild
    user = interaction.user

    try:
        ch = await guild.create_text_channel(
            f"share-{user.name[:12]}",
            overwrites={
                guild.default_role: discord.PermissionOverwrite(read_messages=False),
                user: discord.PermissionOverwrite(read_messages=True, send_messages=True),
                guild.me: discord.PermissionOverwrite(read_messages=True, send_messages=True)
            }
        )
    except Exception as e:
        await interaction.followup.send(f"❌ Error: {e}", ephemeral=True)
        return

    embed = discord.Embed(title="📦 Shared Mods", description=mods if mods else "No mods specified", color=LUMINA_COLOR)
    embed.set_footer(text="Auto-deletes in 1 hour")
    await ch.send(embed=embed)

    class InviteView(discord.ui.View):
        @discord.ui.button(label="Invite Friend", style=discord.ButtonStyle.green, emoji="👥")
        async def invite(self, btn_interaction: discord.Interaction, button: discord.ui.Button):
            await btn_interaction.response.send_message("Type `/invite-friend @username` in this channel", ephemeral=True)

    invite_embed = discord.Embed(title="👥 Invite Friends", description="Click button below", color=LUMINA_COLOR)
    await ch.send(embed=invite_embed, view=InviteView())

    notify = discord.Embed(title="✅ Share Channel Ready", description=f"Channel: {ch.mention}", color=LUMINA_COLOR)
    await interaction.followup.send(embed=notify, ephemeral=True)

@bot.tree.command(name="invite-friend", description="Invite friend to share channel")
async def invite_friend(interaction: discord.Interaction, friend: discord.User):
    if not interaction.channel.name.startswith("share-"):
        await interaction.response.send_message("❌ Only in share channels!", ephemeral=True)
        return

    try:
        await interaction.channel.set_permissions(friend, read_messages=True, send_messages=True)
        await interaction.response.send_message(f"✅ {friend.mention} invited!", ephemeral=True)
        notify = discord.Embed(title="👋 New Member", description=f"{friend.mention} joined!", color=LUMINA_COLOR)
        await interaction.channel.send(embed=notify)
    except Exception as e:
        await interaction.response.send_message(f"❌ Error: {e}", ephemeral=True)

@bot.tree.command(name="close-share", description="Close share channel")
async def close_share(interaction: discord.Interaction):
    if not interaction.channel.name.startswith("share-"):
        await interaction.response.send_message("❌ Only in share channels!", ephemeral=True)
        return

    await interaction.response.defer()
    try:
        await interaction.channel.delete()
    except Exception as e:
        await interaction.followup.send(f"❌ Error: {e}", ephemeral=True)

@bot.tree.command(name="ticket", description="Open support ticket")
async def ticket(interaction: discord.Interaction, title: str, description: str = ""):
    await interaction.response.defer()
    cfg = load_config()
    guild = interaction.guild

    ch = await guild.create_text_channel(f"ticket-{interaction.user.name[:10]}")
    cfg["open_tickets"][str(ch.id)] = {"creator": interaction.user.id}
    save_config(cfg)

    embed = discord.Embed(title=f"🎫 {title}", description=description or "No description", color=LUMINA_COLOR)

    close_btn = discord.ui.Button(label="Close Ticket", style=discord.ButtonStyle.red, emoji="❌")
    close_btn.custom_id = "close_ticket"

    view = discord.ui.View()
    view.add_item(close_btn)

    await ch.send(embed=embed, view=view)
    await interaction.followup.send(f"✅ Ticket: {ch.mention}", ephemeral=True)

@bot.tree.command(name="suggest", description="Submit a suggestion")
async def suggest(interaction: discord.Interaction, title: str, text: str):
    await interaction.response.defer(ephemeral=True)
    guild = interaction.guild
    cfg = load_config()

    # Find suggestions channel by ID first, then by name
    ch = None
    if cfg.get("suggestions_channel_id"):
        ch = guild.get_channel(cfg["suggestions_channel_id"])
    if not ch:
        ch = next((c for c in guild.text_channels if "suggestion" in c.name.lower()), None)
    if not ch:
        await interaction.followup.send("❌ Suggestions channel not found! Use `/setup-server` to create it.", ephemeral=True)
        return

    embed = discord.Embed(title=f"💡 {title}", description=text, color=LUMINA_COLOR, timestamp=datetime.datetime.now())
    embed.set_author(name=str(interaction.user), icon_url=interaction.user.display_avatar.url)
    embed.set_footer(text="👍 / 👎 to vote")

    try:
        msg = await ch.send(embed=embed)
        await msg.add_reaction("👍")
        await msg.add_reaction("👎")
        await interaction.followup.send(f"✅ Suggestion posted in {ch.mention}!", ephemeral=True)
    except Exception as e:
        await interaction.followup.send(f"❌ Error: {e}", ephemeral=True)

@bot.tree.command(name="level", description="Check your level")
async def level_cmd(interaction: discord.Interaction, user: discord.User = None):
    target = user or interaction.user
    cfg = load_config()
    data = cfg["leveling"].get(str(target.id))

    if not data:
        await interaction.response.send_message("No XP yet! Start chatting 🎮", ephemeral=True)
        return

    lvl, xp = data["level"], data["xp"]
    xp_needed = lvl * 100

    e = discord.Embed(title=f"📈 {target.name}'s Level", color=LUMINA_COLOR)
    e.add_field(name="🏆 Level", value=f"**{lvl}**", inline=True)
    e.add_field(name="⚡ XP", value=f"**{xp}/{xp_needed}**", inline=True)
    await interaction.response.send_message(embed=e)

@bot.tree.command(name="leaderboard", description="Top members")
async def leaderboard(interaction: discord.Interaction):
    cfg = load_config()
    lv = cfg.get("leveling", {})

    if not lv:
        await interaction.response.send_message("No data yet!", ephemeral=True)
        return

    top = sorted(lv.items(), key=lambda x: (x[1]["level"], x[1]["xp"]), reverse=True)[:10]
    medals = ["🥇","🥈","🥉"] + ["🏅"]*7

    e = discord.Embed(title="🏆 Leaderboard", color=LUMINA_COLOR)
    desc = ""
    for i, (uid, data) in enumerate(top):
        try:
            u = await bot.fetch_user(int(uid))
            name = u.name
        except:
            name = "Unknown"
        desc += f"{medals[i]} **{name}** — Level {data['level']}\n"

    e.description = desc
    await interaction.response.send_message(embed=e)

@bot.tree.command(name="member-stats", description="Server statistics")
async def member_stats(interaction: discord.Interaction):
    guild = interaction.guild
    cfg = load_config()

    e = discord.Embed(title=f"📊 {guild.name} — Stats", color=LUMINA_COLOR)
    e.add_field(name="👥 Members", value=f"{guild.member_count}", inline=True)
    e.add_field(name="📁 Channels", value=f"{len(guild.text_channels)}", inline=True)
    e.add_field(name="⚠️ Warnings", value=f"{cfg['server_stats']['total_warnings']}", inline=True)

    await interaction.response.send_message(embed=e)

@bot.tree.command(name="warn", description="[MOD] Warn user")
async def warn(interaction: discord.Interaction, user: discord.User, reason: str):
    if not await is_mod(interaction.user):
        await interaction.response.send_message("❌ No permission!", ephemeral=True)
        return

    cfg = load_config()
    uid = str(user.id)
    if uid not in cfg["warnings"]:
        cfg["warnings"][uid] = []

    cfg["warnings"][uid].append({"reason": reason, "warned_at": datetime.datetime.now().isoformat()})
    cfg["server_stats"]["total_warnings"] += 1
    save_config(cfg)

    await interaction.response.send_message(f"⚠️ {user.mention} warned for: {reason}")

@bot.tree.command(name="warnings", description="Show a user's warnings (numbered)")
async def warnings(interaction: discord.Interaction, user: discord.User = None):
    target = user or interaction.user
    cfg = load_config()
    warns = cfg["warnings"].get(str(target.id), [])

    e = discord.Embed(title=f"⚠️ {target.name}'s Warnings", color=discord.Color.orange())
    if warns:
        lines = [f"**#{i+1}** — {w['reason']}" for i, w in enumerate(warns)]
        e.description = f"**{len(warns)} warning(s):**\n" + "\n".join(lines)
        e.set_footer(text="Mods: remove one with /remove-warning user:@name number:#")
    else:
        e.description = "No warnings! ✅"

    await interaction.response.send_message(embed=e, ephemeral=True)

@bot.tree.command(name="remove-warning", description="[MOD] Remove a warning by its number (see /warnings)")
@discord.app_commands.describe(user="The user to remove a warning from", number="The warning number from /warnings (e.g. 1)")
async def remove_warning(interaction: discord.Interaction, user: discord.User, number: int = 1):
    if not await is_mod(interaction.user):
        await interaction.response.send_message("❌ No permission!", ephemeral=True)
        return

    cfg = load_config()
    uid = str(user.id)

    if uid not in cfg["warnings"] or not cfg["warnings"][uid]:
        await interaction.response.send_message(f"❌ {user.mention} has no warnings!", ephemeral=True)
        return

    total = len(cfg["warnings"][uid])
    if 1 <= number <= total:
        removed = cfg["warnings"][uid].pop(number - 1)
        cfg["server_stats"]["total_warnings"] = max(0, cfg["server_stats"].get("total_warnings", 0) - 1)
        save_config(cfg)
        left = len(cfg["warnings"][uid])
        await interaction.response.send_message(f"✅ Removed warning #{number} ({removed['reason']}). {left} left.")
    else:
        await interaction.response.send_message(f"❌ {user.mention} only has **{total}** warning(s). Use a number between 1 and {total}. Check `/warnings` first!", ephemeral=True)

@bot.tree.command(name="kick", description="[MOD] Kick user")
async def kick(interaction: discord.Interaction, user: discord.User, reason: str = "No reason"):
    if not await is_mod(interaction.user):
        await interaction.response.send_message("❌ No permission!", ephemeral=True)
        return

    try:
        await interaction.guild.kick(user, reason=reason)
        await interaction.response.send_message(f"⛔ {user.mention} kicked")
    except Exception as e:
        await interaction.response.send_message(f"❌ Error: {e}", ephemeral=True)

@bot.tree.command(name="ban", description="[MOD] Ban user")
async def ban(interaction: discord.Interaction, user: discord.User, reason: str = "No reason"):
    if not await is_mod(interaction.user):
        await interaction.response.send_message("❌ No permission!", ephemeral=True)
        return

    try:
        await interaction.guild.ban(user, reason=reason)
        await interaction.response.send_message(f"⛔ {user.mention} banned")
    except Exception as e:
        await interaction.response.send_message(f"❌ Error: {e}", ephemeral=True)

@bot.tree.command(name="mute", description="[MOD] Mute a user")
async def mute(interaction: discord.Interaction, user: discord.Member, minutes: int = 10, reason: str = "No reason"):
    if not await is_mod(interaction.user):
        await interaction.response.send_message("❌ No permission!", ephemeral=True)
        return
    try:
        await user.timeout(datetime.timedelta(minutes=minutes), reason=reason)
        cfg = load_config()
        cfg["server_stats"]["total_mutes"] = cfg["server_stats"].get("total_mutes", 0) + 1
        save_config(cfg)
        await interaction.response.send_message(f"🔇 {user.mention} muted for {minutes}m — {reason}")
        await log_action(interaction.guild, "🔇 Mute", user, f"{minutes}m | {reason}", discord.Color.orange())
    except Exception as e:
        await interaction.response.send_message(f"❌ Error: {str(e)[:100]}", ephemeral=True)

@bot.tree.command(name="clear", description="[MOD] Delete messages")
async def clear(interaction: discord.Interaction, amount: int = 10):
    if not await is_mod(interaction.user):
        await interaction.response.send_message("❌ No permission!", ephemeral=True)
        return
    # Defer FIRST so the interaction doesn't time out while purging
    await interaction.response.defer(ephemeral=True)
    try:
        deleted = await interaction.channel.purge(limit=max(1, min(amount, 100)))
        await interaction.followup.send(f"🗑️ Deleted {len(deleted)} messages", ephemeral=True)
        await log_action(interaction.guild, "🗑️ Bulk Delete", interaction.user, f"Count: {len(deleted)}", discord.Color.orange())
    except Exception as e:
        await interaction.followup.send(f"❌ Error: {str(e)[:100]}", ephemeral=True)

@bot.tree.command(name="status", description="Bot and launcher status")
async def status(interaction: discord.Interaction):
    cfg = load_config()
    e = discord.Embed(title="📊 Lumina Status", color=LUMINA_COLOR)
    e.add_field(name="🤖 Bot",        value=f"✅ Online | {round(bot.latency*1000)}ms", inline=True)
    e.add_field(name="🚀 Launcher",   value=f"✅ {cfg.get('launcher_status','online').title()}", inline=True)
    e.add_field(name="📦 Instances",  value=str(len(get_instances())), inline=True)
    e.add_field(name="🎫 Tickets",    value=str(len(cfg["open_tickets"])), inline=True)
    e.add_field(name="⚠️ Warnings",   value=str(cfg["server_stats"]["total_warnings"]), inline=True)
    e.add_field(name="👥 Members",    value=str(interaction.guild.member_count), inline=True)
    await interaction.response.send_message(embed=e)

@bot.tree.command(name="show-instance", description="Show mods for a Lumina instance")
async def show_instance(interaction: discord.Interaction, instance_name: str = None):
    instances = get_instances()
    if not instances:
        await interaction.response.send_message("❌ No instances found! (Bot must run on a PC with the launcher installed)", ephemeral=True)
        return
    target, target_id = None, None
    if instance_name:
        for iid, inst in instances.items():
            if inst.get("name","").lower() == instance_name.lower() or iid.lower() == instance_name.lower():
                target, target_id = inst, iid; break
    if not target:
        if instance_name:
            names = [i.get("name", k) for k, i in instances.items()]
            await interaction.response.send_message(f"❌ Not found! Available: {', '.join(names)}", ephemeral=True)
            return
        target_id, target = next(iter(instances.items()))
    mods = []
    mods_file = LAUNCHER_DATA / target_id / "mods.json"
    if mods_file.exists():
        try:
            with open(mods_file) as f: mods = json.load(f)
        except: pass
    e = discord.Embed(title=f"📦 {target.get('name', target_id)}", color=LUMINA_COLOR)
    e.add_field(name="🎮 MC Version", value=target.get("mcVersion","?"), inline=True)
    e.add_field(name="⚙️ Loader",    value=target.get("loader","VANILLA"), inline=True)
    e.add_field(name="💾 RAM",        value=f"{target.get('ramMinMb',1024)}-{target.get('ramMaxMb',4096)} MB", inline=True)
    if mods:
        mod_text = "\n".join([f"• {m.get('name','?')} v{m.get('version','?')}" for m in mods[:10]])
        if len(mods) > 10: mod_text += f"\n*...and {len(mods)-10} more*"
        e.add_field(name=f"🔧 Mods ({len(mods)})", value=mod_text, inline=False)
    else:
        e.add_field(name="🔧 Mods", value="No mods installed", inline=False)
    await interaction.response.send_message(embed=e)

@bot.tree.command(name="giveaway", description="[ADMIN] Create a giveaway")
async def giveaway(interaction: discord.Interaction, title: str, prize: str, duration_hours: int = 24):
    if not await is_admin(interaction.user):
        await interaction.response.send_message("❌ Admin only!", ephemeral=True)
        return

    cfg = load_config()

    # Find giveaways channel (by ID first, then by name)
    ch = None
    if cfg.get("giveaways_channel_id"):
        ch = interaction.guild.get_channel(cfg["giveaways_channel_id"])
    if not ch:
        ch = next((c for c in interaction.guild.text_channels if "giveaway" in c.name.lower()), None)
    if not ch:
        ch = interaction.channel

    ends_at = (datetime.datetime.now() + datetime.timedelta(hours=duration_hours)).isoformat()
    gid = f"gw{len(cfg['giveaways'])+1}_{interaction.id}"

    embed = discord.Embed(title=f"🎁 {title}", description=f"Prize: **{prize}**\n\nClick the button below to enter!", color=LUMINA_COLOR)
    embed.add_field(name="⏰ Ends in", value=f"{duration_hours} hours", inline=True)
    embed.add_field(name="👥 Participants", value="0", inline=True)
    embed.set_footer(text=f"Giveaway ID: {gid}")

    view = discord.ui.View(timeout=None)
    view.add_item(discord.ui.Button(label="🎁 Join Giveaway", style=discord.ButtonStyle.green, custom_id=f"giveaway_{gid}"))

    # Ping the Event Notifications role (by ID, fallback by name)
    role = None
    if cfg.get("ping_events_role_id"):
        role = interaction.guild.get_role(cfg["ping_events_role_id"])
    if not role:
        role = next((r for r in interaction.guild.roles if "event notification" in r.name.lower()), None)
    ping = role.mention if role else ""

    await interaction.response.defer(ephemeral=True)
    msg = await ch.send(
        content=f"🎉 {ping}" if ping else None,
        embed=embed, view=view,
        allowed_mentions=discord.AllowedMentions(roles=True)
    )

    cfg["giveaways"][gid] = {
        "title": title, "prize": prize, "ends_at": ends_at,
        "message_id": msg.id, "channel_id": ch.id,
        "participants": [], "ended": False
    }
    save_config(cfg)
    await interaction.followup.send(f"✅ Giveaway created in {ch.mention}! ID: `{gid}`", ephemeral=True)

@bot.tree.command(name="announce", description="[ADMIN] Make announcement")
async def announce(interaction: discord.Interaction, title: str, message: str, image: discord.Attachment = None):
    if not await is_admin(interaction.user):
        await interaction.response.send_message("❌ Admin only!", ephemeral=True)
        return

    cfg = load_config()

    # Find announcements channel (by ID first, then by name)
    ch = None
    if cfg.get("announcements_channel_id"):
        ch = interaction.guild.get_channel(cfg["announcements_channel_id"])
    if not ch:
        ch = next((c for c in interaction.guild.text_channels if "announce" in c.name.lower()), None)
    if not ch:
        ch = interaction.channel

    e = discord.Embed(title=f"📣 {title}", description=message, color=LUMINA_COLOR)
    e.set_author(name=str(interaction.user), icon_url=interaction.user.display_avatar.url)

    if image:
        e.set_image(url=image.url)

    # Ping the Update Notifications role (by ID, fallback by name)
    role = None
    if cfg.get("ping_updates_role_id"):
        role = interaction.guild.get_role(cfg["ping_updates_role_id"])
    if not role:
        role = next((r for r in interaction.guild.roles if "update notification" in r.name.lower()), None)
    ping = role.mention if role else ""

    await ch.send(
        content=f"🔔 {ping}" if ping else None,
        embed=e,
        allowed_mentions=discord.AllowedMentions(roles=True)
    )
    await interaction.response.send_message(f"✅ Announced in {ch.mention}!", ephemeral=True)

@bot.tree.command(name="launcher-update", description="[ADMIN] Post update (auto-pulls latest GitHub release link)")
async def launcher_update(interaction: discord.Interaction, version: str, changes: str):
    if not await is_admin(interaction.user):
        await interaction.response.send_message("❌ Admin only!", ephemeral=True)
        return

    await interaction.response.defer(ephemeral=True)
    cfg = load_config()
    old = cfg.get("launcher_version", "?")
    cfg["launcher_version"] = version
    save_config(cfg)
    guild = interaction.guild

    # Pull latest GitHub release for the download link
    rel = await fetch_latest_release()

    rid = cfg.get("ping_updates_role_id")
    role = guild.get_role(rid) if rid else None
    mention = role.mention if role else ""

    # launcher-updates → the LINK (download)
    link = rel["url"] if rel else f"https://github.com/{GITHUB_REPO}/releases"
    update_e = discord.Embed(title=f"🚀 Launcher Update — v{version}", description="A new launcher version is out! 🎉", color=LUMINA_COLOR, url=link)
    update_e.add_field(name="📦 Version", value=f"v{old} → v{version}", inline=True)
    update_e.add_field(name="🔗 Download", value=f"[GitHub Release]({link})", inline=True)

    # changelog → the TEXT (what's new — exactly what you typed, no links)
    changelog_e = discord.Embed(title=f"📋 v{version} — What's New", description=changes, color=LUMINA_COLOR, timestamp=datetime.datetime.now())

    # Post to launcher-updates (fallback announcements) + changelog
    lu = next((c for c in guild.text_channels if "launcher-update" in c.name.lower()), None)
    if not lu and cfg.get("announcements_channel_id"):
        lu = guild.get_channel(cfg["announcements_channel_id"])
    if not lu:
        lu = interaction.channel
    cl = next((c for c in guild.text_channels if "changelog" in c.name.lower()), None)

    ping = f"🔔 {mention}" if mention else None
    am = discord.AllowedMentions(roles=True)
    try:
        await lu.send(content=ping, embed=update_e, allowed_mentions=am)
    except: pass
    if cl and cl.id != getattr(lu, "id", None):
        try: await cl.send(content=ping, embed=changelog_e, allowed_mentions=am)
        except: pass

    await interaction.followup.send(f"✅ Update posted! 📋 Changelog text in {cl.mention if cl else 'changelog'}, 🔗 link in {lu.mention}", ephemeral=True)

@bot.tree.command(name="add-vip", description="[ADMIN] Add VIP role to user")
async def add_vip(interaction: discord.Interaction, user: discord.User):
    if not await is_admin(interaction.user):
        await interaction.response.send_message("❌ Admin only!", ephemeral=True)
        return

    cfg = load_config()
    member = interaction.guild.get_member(user.id)
    if not member:
        await interaction.response.send_message("❌ User not in server!", ephemeral=True)
        return

    vip_role_id = cfg.get("vip_role_id")
    if vip_role_id:
        role = interaction.guild.get_role(vip_role_id)
        if role:
            try:
                await member.add_roles(role)
                conn = sqlite3.connect(DB_FILE)
                c = conn.cursor()
                c.execute("INSERT OR REPLACE INTO vip_bonuses (discord_id, last_bonus_date) VALUES (?, ?)",
                         (user.id, datetime.datetime.now().isoformat()))
                conn.commit()
                conn.close()
                await interaction.response.send_message(f"✅ {user.mention} is now VIP! 💜", ephemeral=True)
                return
            except Exception as ex:
                await interaction.response.send_message(f"❌ Error: {ex}", ephemeral=True)
                return

    await interaction.response.send_message("❌ VIP role not found!", ephemeral=True)

@bot.tree.command(name="remove-vip", description="[ADMIN] Remove VIP role from user")
async def remove_vip(interaction: discord.Interaction, user: discord.User):
    if not await is_admin(interaction.user):
        await interaction.response.send_message("❌ Admin only!", ephemeral=True)
        return

    cfg = load_config()
    member = interaction.guild.get_member(user.id)
    if not member:
        await interaction.response.send_message("❌ User not in server!", ephemeral=True)
        return

    vip_role_id = cfg.get("vip_role_id")
    if vip_role_id:
        role = interaction.guild.get_role(vip_role_id)
        if role:
            try:
                await member.remove_roles(role)
                conn = sqlite3.connect(DB_FILE)
                c = conn.cursor()
                c.execute("DELETE FROM vip_bonuses WHERE discord_id = ?", (user.id,))
                conn.commit()
                conn.close()
                await interaction.response.send_message(f"✅ {user.mention} VIP removed!", ephemeral=True)
                return
            except Exception as ex:
                await interaction.response.send_message(f"❌ Error: {ex}", ephemeral=True)
                return

    await interaction.response.send_message("❌ VIP role not found!", ephemeral=True)

def validate_vip_code(code: str, secret: str) -> bool:
    parts = code.strip().upper().split("-")
    if len(parts) != 3 or parts[0] != "VIP": return False
    if len(parts[1]) != 8 or len(parts[2]) != 6: return False
    expected = hmac_lib.new(secret.encode(), parts[1].encode(), hashlib.sha256).hexdigest()[:6].upper()
    return parts[2] == expected

@bot.tree.command(name="redeem-vip", description="Redeem a VIP code from the LuminaMC launcher shop")
async def redeem_vip(interaction: discord.Interaction, code: str):
    await interaction.response.defer(ephemeral=True)

    secret = os.getenv("VIP_CODE_SECRET", "lumina-vip-2026-squexso")
    code_upper = code.strip().upper()

    if not validate_vip_code(code_upper, secret):
        await interaction.followup.send("❌ Invalid VIP code! Buy one in the LuminaMC launcher shop for 15,000 tokens.", ephemeral=True)
        return

    cfg = load_config()
    used = cfg.get("used_vip_codes", [])
    if code_upper in used:
        await interaction.followup.send("❌ This code has already been redeemed!", ephemeral=True)
        return

    member = interaction.guild.get_member(interaction.user.id)
    if not member:
        await interaction.followup.send("❌ You must be a member of this server!", ephemeral=True)
        return

    vip_role_id = cfg.get("vip_role_id")
    role = interaction.guild.get_role(vip_role_id) if vip_role_id else None
    if not role:
        await interaction.followup.send("❌ VIP role not configured — contact an admin.", ephemeral=True)
        return

    try:
        await member.add_roles(role)
        used.append(code_upper)
        cfg["used_vip_codes"] = used
        save_config(cfg)

        e = discord.Embed(
            title="👑 New Supernova VIP!",
            description=f"{interaction.user.mention} just unlocked VIP! 🎉",
            color=discord.Color.gold()
        )
        e.add_field(name="✅ Role Granted", value=role.mention, inline=False)
        e.set_footer(text="LuminaMC VIP — thank you for your support!")

        vip_ch = None
        if cfg.get("vip_channel_id"):
            vip_ch = interaction.guild.get_channel(cfg["vip_channel_id"])
        if vip_ch:
            try: await vip_ch.send(embed=e)
            except: pass

        await interaction.followup.send(f"🎉 VIP activated! You've been given the {role.mention} role. Welcome to Supernova!", ephemeral=True)
    except Exception as ex:
        await interaction.followup.send(f"❌ Error granting VIP role: {ex}", ephemeral=True)

@bot.tree.command(name="vip-code", description="[ADMIN] Post VIP code")
async def vip_code(interaction: discord.Interaction, code: str, duration: str):
    if not await is_admin(interaction.user):
        await interaction.response.send_message("❌ Admin only!", ephemeral=True)
        return

    # Parse duration (5s, 10m, 2h, 1d)
    time_map = {"s": 1, "m": 60, "h": 3600, "d": 86400}
    try:
        amount = int(duration[:-1])
        unit = duration[-1].lower()
        seconds = amount * time_map.get(unit, 60)
    except:
        await interaction.response.send_message("❌ Invalid format! Use: 5s, 10m, 2h, 1d (e.g., `5s` or `10m`)", ephemeral=True)
        return

    cfg = load_config()

    # Find VIP codes channel by ID first, then by name (fallback)
    ch = None
    if cfg.get("vip_channel_id"):
        ch = interaction.guild.get_channel(cfg["vip_channel_id"])
    if not ch:
        ch = next((c for c in interaction.guild.text_channels if "vip-code" in c.name.lower() or c.name.lower() == "vip"), None)
        if ch:
            cfg["vip_channel_id"] = ch.id
            save_config(cfg)
    if not ch:
        await interaction.response.send_message("❌ VIP channel not found! Use `/setup-server` to create it.", ephemeral=True)
        return

    # Ping the VIP (Supernova) role
    vip_role = None
    if cfg.get("vip_role_id"):
        vip_role = interaction.guild.get_role(cfg["vip_role_id"])
    if not vip_role:
        vip_role = next((r for r in interaction.guild.roles if "supernova" in r.name.lower()), None)
    ping = vip_role.mention if vip_role else ""

    # Send code message
    e = discord.Embed(title="💜 VIP Code", description=f"```{code}```", color=LUMINA_COLOR)
    e.add_field(name="⏱️ Expires In", value=f"`{duration}`", inline=False)
    e.add_field(name="📝 Code", value=f"Copy this code in the Launcher!", inline=False)
    try:
        msg = await ch.send(
            content=f"💜 {ping}" if ping else None,
            embed=e,
            allowed_mentions=discord.AllowedMentions(roles=True)
        )
        cfg["vip_codes"][str(msg.id)] = {"code": code, "expires": (datetime.datetime.now() + datetime.timedelta(seconds=seconds)).isoformat()}
        save_config(cfg)
        await interaction.response.send_message(f"✅ Code posted to VIP channel! Expires in `{duration}`", ephemeral=True)

        # Delete after timeout
        await asyncio.sleep(seconds)
        try:
            await msg.delete()
            logger.info(f"💜 VIP code expired and deleted: {code}")
        except: pass
    except Exception as e:
        await interaction.response.send_message(f"❌ Error: {e}", ephemeral=True)

# Roles the bot manages — (name, cfg_key, color, hoist, permissions)
MANAGED_ROLE_DEFS = [
    ("🛡️ Lumina Core",        "admin_role_id",        LUMINA_COLOR,                       True,  discord.Permissions.all()),
    ("🔨 Orbit Control",      "mod_role_id",          discord.Color.blue(),               True,  discord.Permissions(moderate_members=True, manage_messages=True, kick_members=True, ban_members=True, mention_everyone=True)),
    ("🤖 Lumina Bot",         "bot_role_id",          discord.Color.blurple(),            True,  discord.Permissions.none()),
    ("👑 Supernova",          "vip_role_id",          discord.Color.gold(),               True,  discord.Permissions.none()),
    ("🏆 Level 25+",          "level25_role_id",      discord.Color.orange(),             True,  discord.Permissions.none()),
    ("⭐ Level 10+",          "level10_role_id",      discord.Color.blue(),               True,  discord.Permissions.none()),
    ("🌟 Level 5+",           "level5_role_id",       discord.Color.green(),              True,  discord.Permissions.none()),
    ("🔥 Lumina Veteran",     "veteran_role_id",      LUMINA_COLOR,                       True,  discord.Permissions.none()),
    ("🎮 Cosmic Member",      "member_role_id",       discord.Color.light_grey(),         True,  discord.Permissions.none()),
    ("📢 Update Notifications","ping_updates_role_id", discord.Color.blue(),               False, discord.Permissions.none()),
    ("🎉 Event Notifications","ping_events_role_id",  discord.Color.purple(),             False, discord.Permissions.none()),
]
MANAGED_ROLES = [name for name, *_ in MANAGED_ROLE_DEFS]

def base_name(s):
    """Strip leading emojis/symbols → for matching old roles with different emojis."""
    s = s.strip()
    i = 0
    while i < len(s) and not s[i].isalnum():
        i += 1
    return s[i:].strip().lower()

# Base names (no emoji) of all roles the bot manages — used to clean up old duplicates
MANAGED_ROLE_BASENAMES = {base_name(name) for name in MANAGED_ROLES}

# Full server layout: (category, access, [(channel, type, readonly, cfg_key), ...])
# access: info | chat | vip | staff | voice_member | voice_vip
SERVER_STRUCTURE = [
    ("📢 ANNOUNCEMENTS", "info", [
        ("📣・announcements",    "text", True,  "announcements_channel_id"),
        ("🔄・launcher-updates", "text", True,  None),
        ("📋・changelog",        "text", True,  None),
        ("🔴・launcher-status",  "text", True,  "status_channel_id"),
    ]),
    ("👋 WELCOME", "info", [
        ("👋・welcome",   "text", True, None),
        ("📜・rules",     "text", True, None),
        ("🎭・role-info", "text", True, None),
        ("🔔・get-roles", "text", True, "getroles_channel_id"),
    ]),
    ("💬 COMMUNITY", "chat", [
        ("💬・general",      "text", False, None),
        ("🎮・gaming",       "text", False, None),
        ("🖼️・media",        "text", False, None),
        ("💡・suggestions",  "text", True,  "suggestions_channel_id"),
        ("🤖・bot-commands", "text", False, "botcmds_channel_id"),
        ("🔗・share-codes",  "text", False, None),
    ]),
    ("🎫 SUPPORT", "info", [
        ("📩・create-ticket", "text", True, "tickets_channel_id"),
    ]),
    ("📊 STATS", "info", [
        ("📈・leveling",     "text", True, "leveling_channel_id"),
        ("🏆・leaderboard",  "text", True, "leaderboard_channel_id"),
        ("📊・server-stats", "text", True, "stats_channel_id"),
    ]),
    ("🎉 EVENTS", "info", [
        ("🎁・giveaways", "text", True, "giveaways_channel_id"),
    ]),
    ("👑 VIP LOUNGE", "vip", [
        ("👑・vip-general", "text", False, None),
        ("🎁・vip-codes",   "text", True,  "vip_channel_id"),
        ("🌟・vip-perks",   "text", True,  None),
    ]),
    ("🛡️ STAFF", "staff", [
        ("📋・mod-logs",   "text", True,  "mod_logs_channel_id"),
        ("⚠️・warnings",   "text", False, "warnings_channel_id"),
        ("🔨・mod-chat",   "text", False, None),
        ("💬・staff-chat", "text", False, None),
    ]),
    ("🔊 VOICE", "voice_member", [
        ("🎮 Gaming",  "voice", False, None),
        ("💬 General", "voice", False, None),
        ("🎵 Music",   "voice", False, None),
        ("📢 AFK",     "voice", False, None),
    ]),
    ("👑 VIP VOICE", "voice_vip", [
        ("👑 VIP Lounge", "voice", False, None),
        ("🎮 VIP Gaming", "voice", False, None),
    ]),
    ("⚡ TEMPORARY", "voice_member", [
        ("➕ Join to Create", "voice", False, None),
    ]),
]
MANAGED_CATEGORIES = [cat for cat, _, _ in SERVER_STRUCTURE]
MANAGED_CHANNELS = [ch for _, _, chans in SERVER_STRUCTURE for ch, *_ in chans]
# Old flat names from earlier setups — also cleaned up
LEGACY_CHANNELS = ["announcements", "status", "tickets", "suggestions", "vip", "giveaways"]

@bot.tree.command(name="setup-server", description="[ADMIN] DELETE & rebuild the whole server")
async def setup_server(interaction: discord.Interaction):
    if not await is_admin(interaction.user):
        await interaction.response.send_message("❌ Admin only!", ephemeral=True)
        return

    await interaction.response.defer()
    guild = interaction.guild
    cfg = load_config()
    deleted = 0

    try:
        # ── STEP 1: DELETE old managed roles (match by base name → catches old emojis too) ──
        for role in list(guild.roles):
            if role.managed or role.is_default():
                continue
            if base_name(role.name) in MANAGED_ROLE_BASENAMES:
                try:
                    await role.delete(reason="setup-server reset"); deleted += 1
                    await asyncio.sleep(0.15)
                except: pass

        # ── STEP 2: DELETE old managed categories (+ their channels) & loose channels ──
        del_names = set(MANAGED_CATEGORIES) | set(MANAGED_CHANNELS) | set(LEGACY_CHANNELS)
        for ch in list(guild.channels):
            if isinstance(ch, discord.CategoryChannel):
                continue  # delete categories last
            if ch.name in del_names or (ch.category and ch.category.name in MANAGED_CATEGORIES):
                try:
                    await ch.delete(reason="setup-server reset"); deleted += 1
                except: pass
        for cat in [c for c in guild.categories if c.name in MANAGED_CATEGORIES]:
            try:
                await cat.delete(reason="setup-server reset"); deleted += 1
            except: pass

        # ── STEP 3: CREATE fresh roles ──
        # ALL roles are NON-mentionable → only Mods (mention_everyone perm) can ping any role.
        # Members can still ping individual people (@name) — that is unaffected.
        roles = {}
        for name, cfg_key, color, hoist, perms in MANAGED_ROLE_DEFS:
            try:
                role = await guild.create_role(name=name, color=color, hoist=hoist, mentionable=False, permissions=perms, reason="setup-server")
                cfg[cfg_key] = role.id
                roles[cfg_key] = role
                await asyncio.sleep(0.2)
            except: pass

        everyone = guild.default_role
        r_admin  = roles.get("admin_role_id")
        r_mod    = roles.get("mod_role_id")
        r_vip    = roles.get("vip_role_id")
        r_member = roles.get("member_role_id")

        # Lock @everyone from pinging @everyone/@here/all-roles (only Mods+ can)
        try:
            ep = everyone.permissions
            ep.update(mention_everyone=False)
            await everyone.edit(permissions=ep, reason="setup-server: lock mass pings")
        except: pass

        # Give EVERY member the Cosmic Member role (bots get the Bot role)
        if r_member:
            for m in guild.members:
                try:
                    if m.bot:
                        if r_bot := roles.get("bot_role_id"):
                            if r_bot not in m.roles:
                                await m.add_roles(r_bot)
                    elif r_member not in m.roles:
                        await m.add_roles(r_member)
                        await asyncio.sleep(0.1)
                except: pass

        # ── Permission overwrite builders ──
        def po(**kw): return discord.PermissionOverwrite(**kw)
        VIEW  = dict(view_channel=True, send_messages=False, read_message_history=True)
        CHAT  = dict(view_channel=True, send_messages=True, read_message_history=True, embed_links=True, attach_files=True)
        STAFF = dict(view_channel=True, send_messages=True, read_message_history=True, manage_messages=True)
        BOT   = dict(view_channel=True, send_messages=True, read_message_history=True, manage_messages=True, embed_links=True)
        VC    = dict(view_channel=True, connect=True, speak=True)

        def build_ow(access, readonly):
            ow = {everyone: po(view_channel=False), guild.me: po(**BOT)}
            if access == "info":
                if r_member: ow[r_member] = po(**VIEW)
                if r_mod:    ow[r_mod]    = po(**STAFF)
                if r_admin:  ow[r_admin]  = po(**STAFF)
            elif access == "chat":
                if r_member: ow[r_member] = po(**(VIEW if readonly else CHAT))
                if r_mod:    ow[r_mod]    = po(**STAFF)
                if r_admin:  ow[r_admin]  = po(**STAFF)
            elif access == "vip":
                if r_vip:    ow[r_vip]    = po(**(VIEW if readonly else CHAT))
                if r_mod:    ow[r_mod]    = po(**STAFF)
                if r_admin:  ow[r_admin]  = po(**STAFF)
            elif access == "staff":
                if r_mod:    ow[r_mod]    = po(**STAFF)
                if r_admin:  ow[r_admin]  = po(**STAFF)
            elif access == "voice_member":
                ow = {everyone: po(view_channel=False)}
                if r_member: ow[r_member] = po(**VC)
                if r_admin:  ow[r_admin]  = po(**VC)
            elif access == "voice_vip":
                ow = {everyone: po(view_channel=False)}
                if r_vip:   ow[r_vip]   = po(**VC)
                if r_admin: ow[r_admin] = po(**VC)
            return ow

        # ── STEP 4: CREATE categories + channels ──
        made = {}  # channel name -> channel object
        for cat_name, access, channels in SERVER_STRUCTURE:
            try:
                cat_ow = build_ow(access, False)
                category = await guild.create_category(name=cat_name, overwrites=cat_ow, reason="setup-server")
                await asyncio.sleep(0.25)
            except:
                category = None
            for ch_name, ch_type, readonly, cfg_key in channels:
                try:
                    ow = build_ow(access, readonly)
                    if ch_type == "voice":
                        ch = await guild.create_voice_channel(name=ch_name, category=category, overwrites=ow, reason="setup-server")
                    else:
                        ch = await guild.create_text_channel(name=ch_name, category=category, overwrites=ow, reason="setup-server")
                    made[ch_name] = ch
                    if cfg_key:
                        cfg[cfg_key] = ch.id
                    if ch_name == "➕ Join to Create" and category:
                        cfg["temp_vc_category_id"] = category.id
                    await asyncio.sleep(0.25)
                except: pass

        save_config(cfg)

        # ── STEP 5: FILL channels with content ──
        async def post(ch_name, embed, view=None):
            ch = made.get(ch_name)
            if not ch: return
            try:
                msg = await ch.send(embed=embed, view=view) if view else await ch.send(embed=embed)
                try: await msg.pin()
                except: pass
            except: pass

        # Welcome
        we = discord.Embed(title="👋 Welcome to Lumina MC!", description="The official Discord for the **Lumina Launcher**! 🚀\n\n📜 Read the <#{}> first\n💬 Chat in **#general**\n🎫 Need help? Open a ticket in **#create-ticket**\n🎁 Watch **#giveaways** for prizes!".format(made.get("📜・rules").id if made.get("📜・rules") else 0), color=LUMINA_COLOR)
        await post("👋・welcome", we)

        # Rules
        re_ = discord.Embed(title="📜 Server Rules", description=(
            "**1.** Be respectful — no harassment or hate\n"
            "**2.** No spam or self-promotion\n"
            "**3.** No NSFW content\n"
            "**4.** Use the right channels\n"
            "**5.** Listen to staff 🛡️ Lumina Core / 🔨 Orbit Control\n\n"
            "Breaking rules = warning → mute → ban ⚠️"), color=LUMINA_COLOR)
        await post("📜・rules", re_)

        # Role info
        ri = discord.Embed(title="🎭 Server Roles", description=(
            "🛡️ **Lumina Core** — Admins\n"
            "🔨 **Orbit Control** — Moderators\n"
            "👑 **Supernova** — VIP members (buy in launcher!)\n"
            "🔥 **Lumina Veteran** — Long-time members\n"
            "🏆 **Level 25+** / ⭐ **Level 10+** / 🌟 **Level 5+** — earned by chatting\n"
            "🎮 **Cosmic Member** — everyone\n"
            "📢 **Update Notifications** / 🎉 **Event Notifications** — ping roles"), color=LUMINA_COLOR)
        await post("🎭・role-info", ri)

        # Create-ticket panel with button
        te = discord.Embed(title="🎫 Need Help?", description="Click the button below to open a private support ticket.\nOur staff will help you out! 💪", color=LUMINA_COLOR)
        tview = discord.ui.View(timeout=None)
        tview.add_item(discord.ui.Button(label="📩 Open Ticket", style=discord.ButtonStyle.green, custom_id="open_ticket"))
        await post("📩・create-ticket", te, tview)

        # VIP perks
        ve = discord.Embed(title="🌟 VIP Perks (Supernova 👑)", description=(
            "✅ Exclusive **#vip-general** chat\n"
            "✅ VIP-only voice channels\n"
            "✅ Special token codes in **#vip-codes**\n"
            "✅ Gold name color\n"
            "✅ Priority support\n\n"
            "💜 Buy VIP in the Lumina Launcher!"), color=LUMINA_COLOR)
        await post("🌟・vip-perks", ve)

        # Get-roles panel with self-assign buttons
        gr = discord.Embed(title="🔔 Get Notification Roles", description=(
            "Click a button to get (or remove) a ping role:\n\n"
            "📢 **Update Notifications** — pinged on launcher updates\n"
            "🎉 **Event Notifications** — pinged on giveaways & events"), color=LUMINA_COLOR)
        grview = discord.ui.View(timeout=None)
        grview.add_item(discord.ui.Button(label="📢 Update Notifications", style=discord.ButtonStyle.blurple, custom_id="role_updates"))
        grview.add_item(discord.ui.Button(label="🎉 Event Notifications", style=discord.ButtonStyle.green, custom_id="role_events"))
        await post("🔔・get-roles", gr, grview)

        # Live dashboards (tasks keep these updated afterwards)
        await edit_or_send(made.get("🔴・launcher-status"), build_status_embed(guild, cfg))
        await edit_or_send(made.get("📊・server-stats"),    build_serverstats_embed(guild, cfg))
        await edit_or_send(made.get("🏆・leaderboard"),     build_leaderboard_embed(guild, cfg))

        # Pull the latest GitHub release → launcher-updates (text) + changelog (link)
        rel = await fetch_latest_release()
        if rel:
            cfg["last_github_release"] = rel["tag"]
            save_config(cfg)
            await post("🔄・launcher-updates", build_update_embed(rel))
            await post("📋・changelog", build_changelog_embed(rel))

        # ── Done — report (channel may be deleted, so use DM fallback) ──
        e = discord.Embed(title="✅ Server Rebuilt!", description=f"🗑️ Removed {deleted} old items\n✨ Built {len(SERVER_STRUCTURE)} categories with full structure + content!", color=LUMINA_COLOR)
        e.add_field(name="📋 Categories", value="\n".join(f"{c}" for c, _, _ in SERVER_STRUCTURE), inline=False)
        try:
            await interaction.followup.send(embed=e)
        except:
            try: await interaction.user.send(embed=e)
            except: pass
    except Exception as e:
        try:
            await interaction.followup.send(f"❌ Error: {e}", ephemeral=True)
        except:
            try: await interaction.user.send(f"❌ Setup error: {e}")
            except: pass

@tasks.loop(minutes=60)
async def auto_tasks():
    guild = bot.get_guild(GUILD_ID)
    if not guild: return
    cfg = load_config()

    for tid in list(cfg["open_tickets"].keys()):
        if not guild.get_channel(int(tid)):
            del cfg["open_tickets"][tid]

    save_config(cfg)

@auto_tasks.before_loop
async def before_auto():
    await bot.wait_until_ready()

@tasks.loop(minutes=1)
async def check_giveaways():
    guild = bot.get_guild(GUILD_ID)
    if not guild: return
    cfg = load_config()
    now = datetime.datetime.now()
    changed = False

    for gid, gw in cfg["giveaways"].items():
        if gw.get("ended"): continue
        try:
            ends = datetime.datetime.fromisoformat(gw["ends_at"])
        except:
            continue
        if now < ends: continue

        # Giveaway ended — pick winner
        gw["ended"] = True
        changed = True
        ch = guild.get_channel(gw["channel_id"])
        if not ch: continue

        participants = gw.get("participants", [])
        if not participants:
            await ch.send(f"🎁 Giveaway **{gw['title']}** ended — no participants 😢")
            continue

        winner_id = random.choice(participants)
        winner = guild.get_member(winner_id)
        winner_text = winner.mention if winner else f"<@{winner_id}>"

        e = discord.Embed(title=f"🎉 Giveaway Ended: {gw['title']}", color=LUMINA_COLOR)
        e.add_field(name="🏆 Winner", value=winner_text, inline=False)
        e.add_field(name="🎁 Prize", value=gw["prize"], inline=False)
        e.add_field(name="👥 Total Entries", value=str(len(participants)), inline=False)
        await ch.send(content=f"🎉 Congrats {winner_text}!", embed=e)

    if changed:
        save_config(cfg)

@check_giveaways.before_loop
async def before_giveaways():
    await bot.wait_until_ready()

@tasks.loop(minutes=2)
async def update_status_channels():
    guild = bot.get_guild(GUILD_ID)
    if not guild: return
    cfg = load_config()

    # Launcher status (🔴・launcher-status)
    sc = guild.get_channel(cfg["status_channel_id"]) if cfg.get("status_channel_id") else None
    await edit_or_send(sc, build_status_embed(guild, cfg))

    # Server statistics (📊・server-stats)
    stc = guild.get_channel(cfg["stats_channel_id"]) if cfg.get("stats_channel_id") else None
    await edit_or_send(stc, build_serverstats_embed(guild, cfg))

@update_status_channels.before_loop
async def before_status():
    await bot.wait_until_ready()

@tasks.loop(hours=12)
async def update_leaderboard():
    guild = bot.get_guild(GUILD_ID)
    if not guild: return
    cfg = load_config()
    lc = guild.get_channel(cfg["leaderboard_channel_id"]) if cfg.get("leaderboard_channel_id") else None
    await edit_or_send(lc, build_leaderboard_embed(guild, cfg))

@update_leaderboard.before_loop
async def before_leaderboard():
    await bot.wait_until_ready()

@tasks.loop(minutes=5)
async def cleanup_share_channels():
    import time
    guild = bot.get_guild(GUILD_ID)
    if not guild: return
    cfg = load_config()

    current_time = time.time()
    for owner_id_str, data in list(cfg.get("share_channels", {}).items()):
        created_at = data.get("created_at", 0)
        if current_time - created_at > 3600:
            try:
                for ch in guild.text_channels:
                    if ch.name.startswith("share-"):
                        await ch.delete()
                        break
            except: pass
            if owner_id_str in cfg["share_channels"]:
                del cfg["share_channels"][owner_id_str]
                save_config(cfg)

@cleanup_share_channels.before_loop
async def before_cleanup():
    await bot.wait_until_ready()

@tasks.loop(hours=2)
async def check_github_releases():
    guild = bot.get_guild(GUILD_ID)
    if not guild: return
    cfg = load_config()

    rel = await fetch_latest_release()
    if not rel: return
    if rel["tag"] == cfg.get("last_github_release"): return  # nothing new

    cfg["last_github_release"] = rel["tag"]
    cfg["launcher_version"] = rel["tag"].lstrip("v")
    save_config(cfg)

    # Ping Update Notifications role in announcements (with the "what's new" text)
    ann = guild.get_channel(cfg["announcements_channel_id"]) if cfg.get("announcements_channel_id") else None
    if not ann:
        ann = next((c for c in guild.text_channels if "announce" in c.name.lower()), None)
    role = guild.get_role(cfg["ping_updates_role_id"]) if cfg.get("ping_updates_role_id") else None
    if ann:
        try:
            await ann.send(content=f"🔔 {role.mention}" if role else None, embed=build_update_embed(rel),
                           allowed_mentions=discord.AllowedMentions(roles=True))
        except: pass

    # launcher-updates → LINK | changelog → TEXT — both ping the Update role
    ping = f"🔔 {role.mention}" if role else None
    am = discord.AllowedMentions(roles=True)
    lu = next((c for c in guild.text_channels if "launcher-update" in c.name.lower()), None)
    cl = next((c for c in guild.text_channels if "changelog" in c.name.lower()), None)
    if lu:
        try: await lu.send(content=ping, embed=build_update_embed(rel), allowed_mentions=am)
        except: pass
    if cl:
        try: await cl.send(content=ping, embed=build_changelog_embed(rel), allowed_mentions=am)
        except: pass

@check_github_releases.before_loop
async def before_github():
    await bot.wait_until_ready()

if __name__ == "__main__":
    if not TOKEN:
        print("❌ LUMINA_BOT_TOKEN not set!")
    else:
        try:
            bot.run(TOKEN)
        except Exception as e:
            print(f"❌ Error: {e}")
