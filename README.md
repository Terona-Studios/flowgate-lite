# 🚪 FlowGate Lite
### Lightweight Network Control for BungeeCord & Waterfall

> FlowGate Lite consolidates multiple network behaviors into  
> **one lightweight, predictable plugin** built for modern proxies.

---

## ✨ What FlowGate Lite Does

FlowGate Lite replaces scattered network logic with a **single, focused system**  
that keeps players connected, routed, and informed — even during outages.

No chaos. No disconnect spam. No plugin overload.

---

## 🔀 Smart Hub Routing

- Automatically routes players to available hub servers
- Cycles through configured hubs in a safe, predictable loop
- Works with **any number of hubs**
- Zero hardcoded server names — fully config-driven
- Seamless switching without breaking player state

---

## 🛑 Intelligent Fallback Handling

- Uses a fallback server **only when no hub is available**
- Prevents players from being kicked out of the network
- Overrides proxy disconnects safely and cleanly
- Instantly retries hubs once they come back online
- Fully compatible with proxy priorities

---

## 🎯 Clean Network Entry Flow

- Blocks broken joins when no hub is available
- Prevents reconnect loops and disconnect spam
- Handles:
  - first join
  - hub shutdowns
  - partial outages
  - runtime server crashes
- Keeps players inside the network at all times

---

## ⏳ Fallback Waiting Experience

- Animated titles while players wait in fallback
- Smooth, non-flickering animations
- Automatically starts and stops based on player state
- Fully configurable text and animation frames
- Zero impact on players not in fallback

---

## 🖥️ Advanced MOTD System

- Fully customizable MOTD profiles
- Supports legacy colors and hex colors
- Optional hover text
- Clean separation via `motd.yml`
- Enable or disable instantly without restarts

---

## ⚡ Hub & Slash Commands

- `/hub`-style commands with smart routing
- Automatically skips offline hubs
- Sends players to fallback if no hub is available
- Custom slash commands mapped via config:
  - `/anarchy`
  - `/dev`
  - `/event`
- Permission-based access per server

---

## ⚙️ Configuration Philosophy

- Enable or disable systems independently
- Control routing intervals
- Define hub lists and fallback behavior
- Customize messages, titles, MOTD, and display names
- **Everything is configurable — nothing is forced**

---

## 🧩 Designed for Simplicity

FlowGate Lite intentionally avoids overengineering:

- ❌ No forced queues
- ❌ No heavy background tasks
- ❌ No bloated dependencies
- ✅ Minimal overhead
- ✅ Predictable behavior
- ✅ Easy maintenance

Ideal for **small to medium networks**, development environments,  
and production setups that value stability over complexity.

---

## 🧠 Developed By

**Terona Studios**  
Built with long-term maintainability, transparency,  
and network stability in mind.
