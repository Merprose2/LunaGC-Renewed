<div align="center">

# LunaGC-Renewed 7.0.0

**An updated version of Grasscutters/LunaGC, with some new features implemented.**

[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)](https://www.oracle.com/java/technologies/javase/jdk25-archive-downloads.html)
[![Version](https://img.shields.io/badge/version-7.0.0-blue?style=flat-square)](#)

**[Read the handbook!](handbook.md)**

</div>

> [!NOTE]
> Features and functionality of the PS are not guaranteed. Try it yourself to
> see what works and what doesn't.

---

## Requirements

| Dependency | Purpose |
| --- | --- |
| [Java 25 (JDK)](https://www.oracle.com/java/technologies/javase/jdk25-archive-downloads.html) | Running & building the server |
| [MongoDB Community Server](https://www.mongodb.com/try/download/community) | Database |
| [NodeJS 20](https://nodejs.org/dist/v20.15.0/node-v20.15.0-x64.msi) | Handbook generation |
| [Git](https://git-scm.com) | Cloning the repository |

Make sure Java is installed and your environment variables are set.

---

## Setup Guide

### 1. Clone the repository

```bash
git clone https://github.com/Merprose2/LunaGC-Renewed.git
```

### 2. Build the server

> [!TIP]
> Handbook generation may fail on some systems. To disable it, append
> `-PskipHandbook=1` to the `gradlew jar` command.

**Windows**

```shell
.\gradlew.bat
.\gradlew.bat jar
```

**Linux**

```bash
chmod +x gradlew
./gradlew
./gradlew jar
```

The output JAR will be in the project root folder.

To compile the handbook manually:

```shell
./gradlew generateHandbook
```

### 3. Install the resources

<details open>
<summary><b>Regular method</b></summary>

Download the [resources](https://github.com/Merprose2/LunaGCR-Resources), create
a folder named `resources` inside the LunaGC folder, and extract the resources
into it.

### 4. Configure

Set `useEncryption`,  and `useInRouting` to `false`. They should be
`false` by default — if not, change them.

If you want Quest set `Questing` to `true`

### 5. Patch the game

Put [Astrolabe.dll](https://github.com/Merprose2/LunaGC-Renewed/tree/7.0/patch)
in the game folder at `GenshinImpact_Data/Plugins`.

> [!IMPORTANT]
> Back up the original `Astrolabe.dll` before replacing it.

- **To disable the patch:** rename `Astrolabe.dll` so it's either not a `.dll`
  or not named Astrolabe (e.g. `Astrolabe.deleleu` or `astrollable.dll`).
- **If you use Cultivation:** put the file in the `Cultivation/patch` directory
  and rename it to `6version.dll`. Back up the original DLL first.

### 6. Run it

Start the server and the game, then create an account in the LunaGC console.

Have fun!

---

## Troubleshooting

<details>
<summary><b>Connection or routing errors</b></summary>

Make sure `useEncryption` and `useInRouting` are both set to `false`.

</details>

<details>
<summary><b>Windy scripts not loading</b></summary>

Put your `.luac` files in `C:\Windy` — create the folder if it doesn't exist.

</details>

<details>
<summary><b>MongoDB connection timeout</b></summary>

Check whether the MongoDB service is running.

**Windows** — press <kbd>Win</kbd> + <kbd>R</kbd>, type `services.msc`, find
the MongoDB server entry, and if it isn't started, right-click it and start it.

**Linux** — check the status:

```bash
systemctl status mongod
```

If it isn't running:

```bash
systemctl start mongod
```

If you get **error 14**, change the ownership of the MongoDB folder and socket
file, then start the service again:

```bash
sudo chown -R mongodb:mongodb /var/lib/mongodb
sudo chown mongodb:mongodb /tmp/mongodb-27017.sock
```

</details>

---

## Credits

- [Rafs-kk](https://github.com/Rafs-kk)
- [Hartie95](https://github.com/Hartie95)
- [Mar7thLover](https://github.com/Mar7thLover)

Proto repository: [NahidaImpact-protos](https://github.com/Mar7thLover/NahidaImpact-Server/tree/main/Proto)
