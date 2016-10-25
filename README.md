# gradle-showGraph
A simple Gradle plugin that shows a task execution graph (in real execution order) with task descriptions to a Gradle task.

The main purpose for the plugin is the generation of pretty overviews on what a task really does (prerequisitions are good task descriptions for each task in the project). It should save time, because new developers (or whoever wants to execute the build tasks) get a **dynamic** and **always up-to-date** overview on what each task does.

See the plugin in plugins.gradle.com -> https://plugins.gradle.org/plugin/com.silverjan.plugins.show-graph-plugin

## How to Use in Your Project
Add the following lines in your build.gradle

	plugins {
	  id "com.silverjan.plugins.show-graph-plugin" version "1.0.4"
	}
	
## How to Use the Plugin
To show the execution graph of any task in your project, execute 

    gradlew <taskname> showGraph

To show the simple execution graph (no tree-style for depending tasks and no task descriptions) of any task in your project, execute 

    gradlew <taskname> showGraph -Psimple
    
## What the Plugin is Not Able to (.. or what hasn't been tested)
Not able to (== graph will be falsy / error during build)

- Running multiple tasks in the execution command (e.g. *gradlew taskA taskB showGraph*)

Not tested

- Run tasks in multiproject-project (only tested in standalone-project)
