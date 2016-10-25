package com.bosch.gradle

import org.gradle.api.*
import java.util.Map.Entry

class ShowGraphPlugin implements Plugin<Project> {
	Project prj
	int depCount = 0
	LinkedHashMap<Task, Integer> allTasksMap
	String showGraphTaskName = 'showGraph'

	final String ANSI_RESET = "\u001B[0m"
	final String ANSI_YELLOW = "\u001B[33m"
	final String ANSI_BLUE = "\u001B[34m"

	void apply(Project project) {
		prj = project
		prj.task(showGraphTaskName) {
			prj.gradle.taskGraph.whenReady {
				if (prj.gradle.taskGraph.hasTask(prj.tasks[name])) {
					prj.gradle.taskGraph.getAllTasks().each {
						if (it.name != name) it.setEnabled(false)
					}
				}
			}
			doLast { project.hasProperty('simple') ? printGraphSimple() : showGraphImpl() }
		}
	}

	def void printGraphSimple() {
		printProjectInformation()
		prj.gradle.taskGraph.getAllTasks().each {task -> println task }
		printOptions()
	}

	/*
	 * Implementation of the showGraph function
	 */
	def void showGraphImpl() {
		def allTasks = prj.gradle.taskGraph.getAllTasks()
		allTasksMap = setTaskLevelsInit(allTasks) // <Task, level>
		LinkedHashMap<Task, Integer> reorderInfoMap = new HashMap<Task, Integer>() // <key, newIndex>

		for (int i = 0; i != allTasksMap.size(); i++) {
			Task currentTask = allTasksMap.keySet()[i]
			def deps = getDepOn(currentTask.name)
			if (deps != null) {
				countDeps(currentTask.name)
				incrementTaskLevels(i - depCount, i-1)
				reorderInfoMap.put(currentTask, i - depCount)
				depCount = 0
			}
		}

		reorderMap(reorderInfoMap)
		printFullGraph()
	}

	/*
	 * Reorder the allTasksMap instance with the information of the second parameter
	 */
	def void reorderMap(Map<Task, Integer> reorderInfoMap) {
		reorderInfoMap.keySet().each {Task taskToReorder ->
			Integer newIndex = reorderInfoMap.get(taskToReorder)
			Integer clipboardValue = allTasksMap.get(taskToReorder)

			allTasksMap.remove(taskToReorder)
			addToIndexInLinkedHashMap(allTasksMap, newIndex, taskToReorder, clipboardValue)
		}
	}

	/*
	 * Add a <K,V> entry to a LinkedHashMap to the given index
	 */
	def <K, V> void addToIndexInLinkedHashMap(LinkedHashMap<K, V> map, int index, K key, V value) {
		assert (map != null)
		assert !map.containsKey(key)
		assert (index >= 0) && (index < map.size())

		int i = 0
		List<Entry<K, V>> rest = new ArrayList<Entry<K, V>>()
		for (Entry<K, V> entry : map.entrySet()) {
			if (i++ >= index) {
				rest.add(entry)
			}
		}
		map.put(key, value)
		for (int j = 0; j < rest.size(); j++) {
			Entry<K, V> entry = rest.get(j)
			map.remove(entry.getKey())
			map.put(entry.getKey(), entry.getValue())
		}
	}

	/*
	 * Increment the task levels in the allTasksMap instance (subset from -> to)
	 */
	def void incrementTaskLevels(int from, int to) {
		for (int i = 0; i != allTasksMap.size(); i++) {
			if (i >= from  && i <= to) {
				Task key = allTasksMap.keySet()[i]
				allTasksMap.put(key, allTasksMap.get(key) + 1)
			}
		}
	}

	/*
	 * Set task levels for all tasks to 0
	 */
	def LinkedHashMap<Task, Integer> setTaskLevelsInit(Collection tasks) {
		LinkedHashMap<Task, Integer> map = new LinkedHashMap<Task, Integer>()
		tasks.each {task ->
			map.put(task, 0)
		}
		return map
	}

	/*
	 * Counts all dependencies of a task including the dependencies of the subtasks. See the int value in class property depCount.
	 */
	def void countDeps(String taskName) {
		def taskDepOn = getDepOn(taskName)
		if (taskDepOn != null) {
			taskDepOn.each {depTask ->
				depCount++
				depTask instanceof String ? countDeps(depTask) : countDeps(depTask.name)
			}
		}
	}

	/*
	 * Returns null if a task has no dependencies OR a Collection of tasks.
	 * Attention: The Collection instance does only contain the first generation sub children (sub depending tasks)
	 */
	def Collection getDepOn(taskName) {
		Collection returnValue
		def taskDepOn = prj.tasks[taskName].getDependsOn()
		if (taskDepOn instanceof Collection && taskDepOn.size() == 1) {
			returnValue = null
		} else {
			taskDepOn.each {dep ->
				if (dep instanceof Collection) {
					assert returnValue == null : "Yo Dev! This shouldn't happen. returnValue will be overwritten in the next action!"
					returnValue = filterInputFiles(dep)
				} else if (dep instanceof Task && dep != "task '$dep.name' input files") {
					if (returnValue == null) { returnValue = [] }
					returnValue << dep
				}
			}
		}
		return returnValue
	}

	/*
	 * Takes a Collection instance from tasks.task.getDependsOn() and filters potential "task '$task.name' input files" as they are not relevant for showGraph
	 */
	def Collection filterInputFiles(Collection unfiltered) {
		def filtered = []
		unfiltered.each { task ->
			if (task instanceof String) {
				if (task != "task '$task' input files") filtered << prj.tasks[task]
			} else if (task instanceof Task){
				if (task.toString() != "task '$task.name' input files") filtered << task
			}
		}
		return filtered
	}
	
	/*
	 * ###############
	 * ## P R I N T ##
	 * ###############
	 */
	
	/*
	 * Show the graph in the CLI
	 */
	def void printFullGraph() {
		printProjectInformation()

		// find out the maximum task name length + the intends caused by levels
		int maxTaskNameLength = 10 // default
		int maxLevel = 0 // default
		allTasksMap.keySet().each { key ->
			if (key.name.toString().length() > maxTaskNameLength) {
				maxTaskNameLength = key.name.toString().length()
			}
			if (allTasksMap.get(key) > maxLevel) {
				maxLevel = allTasksMap.get(key)
			}
		}
		for (int i = 0; i != maxLevel; i++) {maxTaskNameLength = maxTaskNameLength + 4}

		// display the tasks and subtasks
		allTasksMap.keySet().each { key ->
			if (key.name.equals(showGraphTaskName)){
				return
			}
			Integer level = allTasksMap.get(key)
			String showLine = ""
			for (int i = 0; i != level; i++) {
				if (i == level - 1) {
					showLine += "$ANSI_YELLOW+-- $ANSI_RESET"
				} else {
					showLine += "    "
				}
			}
			showLine += ":$key.name"
			String template = ""
			if (level == 0) {
				template = "%-${maxTaskNameLength + 1}s %s\n"
			} else {
				template = "%-${maxTaskNameLength + 10}s %s\n"
			}
			printf (template, showLine, key.description)
		}
		printOptions()
	}

	/*
	 * Print the project information as a header for the graph
	 */
	def void printProjectInformation() {
		int headerLength = prj.toString().length()
		String header = "\n"
		for (int i = 0; i != headerLength; i++) {header += "-"}
		header += "\n"
		header += "$ANSI_YELLOW${prj.toString().toUpperCase()}$ANSI_RESET\n"
		for (int i = 0; i != headerLength; i++) {header += "-"}
		header += "\n"
		println header
	}

	/*
	 * Print the options for the task
	 */
	def void printOptions() {
		println "\nTo show the execution graph of another task, execute 'gradlew <taskname> $showGraphTaskName'"
		println "To show the simple execution graph of a task, execute 'gradlew <taskname> $showGraphTaskName -Psimple'\n"
	}
}
