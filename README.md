# LunaGC-7.0.0

## Updated version of Grasscutters/LunaGC, with some new features implemented.
Features and functionality of the PS is not guaranteed, try it yourself to see what works and what doesnt.

# Read the [handbook](handbook.md)!

# Setup Guide
- Read it below, its just enough to get the server up and running along with the client.

## Main Requirements

- Get [Java 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
- Get [MongoDB Community Server](https://www.mongodb.com/try/download/community)
- Get [NodeJS](https://nodejs.org/dist/v20.15.0/node-v20.15.0-x64.msi) (For handbook generation)
- Make sure to install java and set the environment variables.
- Build the server (refer to "Compile the actual server" in this guide.)

- Install the resources using one of these methods:
-  **Regular method:** Download the [Resources](https://github.com/Merprose2/LunaGCR-Resources), create a folder named `resources` inside the LunaGC folder, and extract the resources into it.
-  **Resource cache method:** Download `LunaGC-6.6-resources.cache` from the [latest Resource Cache release](https://github.com/Merprose2/LunaGCR-Resources/releases/tag/latest-cache), create a folder named `cache` inside the LunaGC folder, and place the file inside it without extracting or renaming it.
-  The server will automatically use a valid resource cache when available and fall back to the normal `resources` folder otherwise.
- Set useEncryption, Questing and useInRouting to false (it should be false by default, if not then change it).
- [Patch the game](#patching-the-game)
- Start the server and the game, make sure to also create an account in the LunaGC console.
- Have fun!

### Patching the game
- Put [Astrolabe.dll](https://github.com/Merprose2/LunaGC-Renewed/tree/7.0/patch) in the game folder at `GenshinImpact_Data/Plugins`. Make sure you back up the old `Astrolabe.dll` in the plugins folder.
- To "disable" the patch, just rename Astrolabe.dll to something else so it's not a DLL or don't name it Astrolabe (for example Astrolabe.deleleu / astrollable.dll).
- If you use Cutivation, put the file in the `Cultivation/patch` directory and rename it to `6version.dll`. Make sure you also back up the original dll before replacing it.

### Getting started

- Clone the repository (install [Git](https://git-scm.com) first )

  ```
  git clone https://github.com/Merprose2/LunaGC-Renewed.git
  ```

- Now you can continue with the steps below.


### Compile the actual Server

**Requirements**:

[Java Development Kit 17 | JDK](https://oracle.com/java/technologies/javase/jdk17-archive-downloads.html) or higher

- **Sidenote**: Handbook generation may fail on some systems. To disable handbook generation, append `-PskipHandbook=1` to the `gradlew jar` command.

- **For Windows**:

  ```shell
  .\gradlew.bat
  .\gradlew.bat jar
  ```

- **For Linux**:

  ```bash
  chmod +x gradlew
  ./gradlew
  ./gradlew jar
  ```

### You can find the output JAR in the project root folder.

### Manually compile the handbook

```shell
./gradlew generateHandbook
```

## Troubleshooting

- Make sure to set useEncryption and useInRouting both to false otherwise you might encounter errors.
- To use windy make sure that you put your luac files in C:\Windy (make the folder if it doesnt exist)
- If you get an error related to MongoDB connection timeout, check if the mongodb service is running. On windows: Press windows key and r then type `services.msc`, look for mongodb server and if it's not started then start it by right clicking on it and start. On linux, you can do `systemctl status mongod` to see if it's running, if it isn't then type `systemctl start mongod`. However, if you get error 14 on linux change the owner of the mongodb folder and the .sock file (`sudo chown -R mongodb:mongodb /var/lib/mongodb` and `sudo chown mongodb:mongodb /tmp/mongodb-27017.sock` then try to start the service again.)

## Credit

Proto Repository [hk4e-protos](https://gitlab.com/CarolBicsi/genshin-protocol)

Patch Repository [hk4e-patch-universal](https://github.com/capyb2222/animegamepatch)

Original Repository [kitkat033](https://github.com/kitkat033/)
