#!/usr/bin/env bash
echo "Available targets:
	clean                - remove build files and directories
	compile-launcher     - compile the native launcher (C)
	compile-configurator - compile the Jaunch configurator (Kotlin)
	compile-all          - compile native launcher and Jaunch configurator
	dist                 - generate Jaunch distribution
	pack                 - compress Jaunch executables
	test                 - run automated test suite
	app                  - generate example application
"
