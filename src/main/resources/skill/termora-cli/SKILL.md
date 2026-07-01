---
name: termora-cli
description: This skill should be used when the user asks to "connect to a server", "SSH into a machine", "run a command on a remote host", "list SSH assets/hosts", "upload/download files to a server", or mentions the `termora-cli` command — including Chinese phrasings like "连接服务器"、"登录某台机器跑命令"、"上传/下载文件到服务器"、"列出主机". Use `termora-cli` instead of running `ssh`, `scp`, or `sftp` directly.
---

# Termora CLI

Use the `termora-cli` command to list SSH assets, run one-off commands, and transfer files through the host configurations the user has already saved in their **Termora** desktop app.

Prefer `termora-cli` over running `ssh`, `scp`, or `sftp` directly. Termora holds the credentials and connects using saved configurations, so you never need to ask the user for passwords, private keys, or passphrases.

Run `termora-cli --help` to discover commands. Output format is decided automatically by whether stdout is a TTY: when piped or captured programmatically it prints **JSON**, in an interactive terminal it prints human-readable text. You normally do **not** need to pass `--json` — it is accepted only by `assets list` as a compatibility placeholder and does not change the output; `exec`/`upload`/`download` do not accept `--json` (writing it would be treated as part of the command).

## Common Workflow

```bash
termora-cli assets list -q <keyword>               # find which hosts you may use (by name/host/remark)
termora-cli exec <asset-id> "<command>"            # run one command on that host
termora-cli upload <asset-id> <local> <remote>     # send a file to the host
termora-cli download <asset-id> <remote> <local>   # fetch a file from the host
```

Always start with `assets list` to discover the `<asset-id>` to use. **Only hosts the user has explicitly allowed for termora-cli access are listed** — if a host you expect is missing, ask the user to enable it in Termora first (Settings →「命令行工具」/ Command Line, then check that host).

Each listed asset includes a `protocol` field. `exec`, `upload`, and `download` work over SSH only — when choosing an id, confirm its `protocol` is `SSH` (using these commands on a non-SSH asset will fail to connect, exit code 5).

## Running Commands

`termora-cli exec` takes an asset id and one complete remote shell command string, opens a temporary SSH connection, runs the command, and closes the connection. It is **stateless** — each call is a fresh connection, so `cd`, `export`, and shell state do **not** persist between calls. Combine steps in a single command when you need shared state:

```bash
termora-cli exec <asset-id> "cd /var/log && tail -n 50 syslog"
```

For simple commands, pass one complete remote shell command string as `<command>`.

When running from **Windows PowerShell**, pass complex commands through stdin if they contain quotes, pipes, redirects, JSON, `$`, backticks, or nested shell code — this avoids PowerShell's quoting pitfalls:

```powershell
@'
<command>
'@ | termora-cli exec <asset-id> --stdin
```

In Windows PowerShell, pass simple commands as one quoted `<command>` and do **not** split command words into multiple arguments. For anything complex, use `--stdin` with a single-quoted here-string instead.

## File Transfer

```bash
termora-cli upload <asset-id> ./app.tar.gz /tmp/release.tar.gz
termora-cli download <asset-id> /tmp/app.tar.gz ./latest.tar.gz
```

Destination paths follow **scp-style** behavior: if the destination exists and is a directory, the source keeps its name inside that directory; otherwise the destination path is used as the final file path. Transfers are currently **single-file** (folder recursion is not yet supported).

## Exit Codes

`termora-cli` exits non-zero on failure. Read the exit code to decide what to do next:

| Code | Meaning | What to do |
|------|---------|-----------|
| 0 | Success | — |
| 1 | Usage error | Fix the command syntax |
| 2 | Asset not found | Re-run `assets list` to get a valid id |
| 3 | Host not allowed (not whitelisted) | Ask the user to allow that host for termora-cli in Termora (Settings → Command Line) |
| 4 | Dangerous command blocked | The command matched a destructive-command guard (e.g. `rm -rf /`, `mkfs`, `shutdown`); rephrase or confirm intent with the user |
| 5 | Connection / execution failed | See "First-connection failures" below |
| 6 | termora-cli is disabled | Ask the user to turn on "Enable termora-cli" in Termora settings → Command Line |

**Important — `exec` passes through the remote command's exit code.** Once the connection and authentication succeed, `termora-cli exec` exits with the **remote command's own exit status** (e.g. a remote `grep` with no match exits 1, `command not found` exits 127), which can be greater than 5. An exit code of **255** means the remote exit status could not be obtained (the connection itself succeeded). Therefore the 1–5 meanings above apply to `termora-cli`'s **own** failures (usage / not-found / not-allowed / dangerous / connection); to judge whether the remote command actually ran and succeeded, **prefer reading the `exitCode` and `stderr` fields in the JSON output** rather than the process exit code alone. `upload`/`download` only ever exit 0 (success) or 5 (failure); they do not pass through other codes.

## Rules & Caveats

- **Never ask the user for SSH passwords, private keys, or passphrases.** Termora holds those and connects via saved configurations.
- **Whitelist:** only hosts the user has allowed for termora-cli access are reachable. `assets list` shows exactly those; any other id returns exit code 3.
- **Dangerous commands are blocked** (exit code 4) as a guardrail against accidents — it is a conservative string-level check, not a full sandbox. If you genuinely need such an operation, confirm with the user and have them run it.
- **Audit:** every command and transfer is logged by Termora. Assume your actions are recorded.
- **Long-running tasks** should be detached on the remote host with `tmux`, `nohup`, or `systemd` — since `exec` closes the connection when the command returns, a foreground long task would be tied to that single call.
- **Disabled state:** if any command exits with code 6, termora-cli is turned off — ask the user to enable it in Termora (Settings → Command Line) before retrying.

### First-connection failures (exit code 5)

`termora-cli` runs without Termora's GUI prompts. A host that needs **interactive input on first connect** can therefore fail, specifically:

- a server that only offers **keyboard-interactive** authentication, or
- a host whose **key/passphrase or host-key trust** has never been established.

If `exec`/`upload`/`download` fails to connect, ask the user to **open that host once in the Termora GUI** to cache trust/credentials, then retry.
