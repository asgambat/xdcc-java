# xdcc-java

A command-line XDCC IRC downloader written in Java 21, built with [Micronaut](https://micronaut.io/) + [PicoCLI](https://picocli.info/).

Search for XDCC packs across multiple search engines and download them via IRC DCC protocol.

## Features

- **3 CLI commands**: direct download, search, interactive browse & download
- **4 search engines**: xdcc-eu, ixirc, nibl, subsplease
- **2 IRC backends**: [PircBotX](https://github.com/pircbotx/pircbotx) (default), [KittehIRCClientLib](https://github.com/KittehOrg/KittehIRCClientLib) — switchable at runtime
- **DCC resume** support for interrupted downloads
- **GraalVM native image** support (Linux ARM64 via GitHub Actions)
- **Virtual threads** (Java 21) for lightweight concurrency

## Requirements

- Java 21+ (for JAR execution)
- Maven 3.9+ (for building)
- GraalVM CE 21 (optional, for native compilation)

## Build

```bash
# Standard JAR (fat JAR with all dependencies)
mvn package

# Native executable (requires GraalVM)
mvn package -Dpackaging=native-image -DskipTests
```

## Usage

### Run

```bash
# With JAR
java -jar target/xdcc-java-1.0.0-SNAPSHOT.jar <command> [options]

# With native executable
./xdcc-java <command> [options]
```

### Commands

#### `xdcc-search` — Search for XDCC packs

```bash
xdcc-java xdcc-search "search term" [engine]
```

| Option | Description | Default |
|--------|-------------|---------|
| `-e, --search-engine` | Search engine to use | `xdcc-eu` |
| `-v` | Verbose output | `false` |

Available search engines: `xdcc-eu`, `ixirc`, `nibl`, `subsplease`

**Examples:**
```bash
xdcc-java xdcc-search "ubuntu iso"
xdcc-java xdcc-search "one piece" -e ixirc
xdcc-java xdcc-search "anime" subsplease
```

#### `xdcc-browse` — Interactive search & download

Search for packs and interactively select which ones to download.

```bash
xdcc-java xdcc-browse "search term" [options]
```

| Option | Description | Default |
|--------|-------------|---------|
| `-e, --search-engine` | Search engine | `xdcc-eu` |
| `-x, --ext` | Filter by file extensions (comma-separated) | — |
| `-b, --bot` | Filter by bot name (case-insensitive) | — |
| `-s, --server` | IRC server override | — |
| `-o, --out` | Output directory | `.` |
| `-t, --throttle` | Speed limit (e.g. `1M`, `500K`) | unlimited |
| `-c, --connect-timeout` | Connect timeout (seconds) | `120` |
| `-S, --stall-timeout` | Stall timeout (seconds) | `60` |
| `-f, --fallback-channel` | Fallback IRC channel | — |
| `-w, --wait-time` | Wait before XDCC request (seconds) | `0` |
| `-u, --username` | IRC nickname | random |
| `-d, --channel-join-delay` | Delay after joining channel (seconds, -1 = random 5-10) | `-1` |
| `--dns-server` | Fallback DNS server | — |
| `--irc-library` | IRC library: `pircbotx` or `kitteh` | `pircbotx` |
| `-v` | Verbose | `false` |
| `-vv` | Very verbose | `false` |
| `-q` | Quiet | `false` |
| `-qq` | Very quiet | `false` |

**Interactive selection formats:**
- Single: `3`
- Range: `1-5`
- Count from start: `1+5` (5 items starting at 1)
- List: `1,3,5`
- All: `all`

**Examples:**
```bash
xdcc-java xdcc-browse "movie title" -x mkv,avi
xdcc-java xdcc-browse "anime" -e nibl -b "bot_name" -o /downloads
xdcc-java xdcc-browse "series" -t 1M --irc-library kitteh
```

#### `xdcc-dl` — Direct download

Download a specific pack using an XDCC message.

```bash
xdcc-java xdcc-dl "/msg BotName xdcc send #42" [options]
```

Supports all download options from `xdcc-browse` (server, output, throttle, timeouts, etc.).

**Examples:**
```bash
xdcc-java xdcc-dl "/msg MyBot xdcc send #123"
xdcc-java xdcc-dl "/msg MyBot xdcc send #123" -s irc.example.com -o /downloads
```

## Project Structure

```
src/main/java/io/github/asgambat/xdcc/
├── XdccApplication.java          # Entry point
├── command/
│   ├── DlCommand.java            # xdcc-dl command
│   ├── SearchCommand.java        # xdcc-search command
│   └── BrowseCommand.java        # xdcc-browse command
├── domain/                       # Domain models (XdccPack, IrcServer)
├── downloader/                   # Download orchestration
├── irc/
│   ├── IrcClient.java            # IRC client interface
│   ├── IrcEventHandler.java      # IRC event callback interface
│   ├── IrcClientFactory.java     # Factory interface
│   ├── DefaultIrcClientFactory.java
│   ├── XdccIrcClient.java        # XDCC/DCC protocol logic
│   ├── pircbotx/                 # PircBotX implementation
│   └── kitteh/                   # KittehIRC implementation
├── parse/                        # Message & throttle parsers
└── search/                       # Search engine implementations
    ├── SearchEngine.java
    ├── XdccEuEngine.java
    ├── IxircEngine.java
    ├── NiblEngine.java
    └── SubsPleaseEngine.java
```

## Native Image (GraalVM)

A GitHub Actions workflow builds a native ARM64 Linux binary on every tag push:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The binary is available as a build artifact in the **Actions** tab. You can also trigger the build manually via **workflow_dispatch**.

The native build uses:
- GraalVM CE 21 on `ubuntu-24.04-arm` runner
- Serial GC (low memory footprint)
- `-O2` optimization and `strip` for a compact binary

## License

MIT
