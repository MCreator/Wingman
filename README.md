# MCreator Wingman

MCreator Wingman is a Java plugin for MCreator that adds an agent assistant dock to the MCreator workspace window.
It uses ACP protocol to communicate with the selected agent.

# Setup

In order for the plugin dev environment to work, make a new file called `gradle.properties` with the following contents:

```
mcreator_path=<path to MCreator core Gradle project directory>
```

# Running MCreator with the plugin

This plugin comes with some Gradle tasks to help you with the development of the plugin.

* `runMCreatorWithPlugin`: Run MCreator with the plugin loaded

There is also IntelliJ IDEA run configuration for this task provided in the repository.

# Exporting

To export the plugin, run `jar` task and find the plugin zip file in `build/libs`.

# License

MCreator Wingman is licensed under the GPL-3.0 license if not otherwise stated in source files or other files of
this project. Copyright 2026 Pylo and contributors.

The plugin bundles several third-party libraries. License files and notices for these libraries are located in the
`license` subdirectory and are shipped with the plugin.
