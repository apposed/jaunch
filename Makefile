help:
	@echo "Available targets:\n\
		clean            - remove build files and directories\n\
		compile-launcher - compile the native launcher (C)\n\
		compile-config   - compile the Jaunch configurator (Kotlin)\n\
		compile-all      - compile native launcher and Jaunch configurator\n\
		app              - generate example application\n\
		test             - run automated test suite\n\
	"

clean:
	@echo -e "\n\
	\033[1;33m[clean]\033[0m"
	bin/clean.sh

compile-launcher:
	@echo "\n\
	\033[1;33m[compile-launcher]\033[0m"
	bin/compile-launcher.sh

compile-config:
	@echo "\n\
	\033[1;33m[compile-config]\033[0m"
	bin/compile-config.sh

compile-all: compile-launcher compile-config

app: compile-all
	@echo "\n\
	\033[1;33m[app]\033[0m"
	bin/app.sh

test: app
	@echo "\n\
	\033[1;33m[test]\033[0m"
	bin/test.sh

.PHONY: tests
