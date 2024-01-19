help:
	@bin/help.sh

clean:
	@echo -e "\n\
	\033[1;33m[clean]\033[0m"
	bin/clean.sh

compile-launcher:
	@echo -e "\n\
	\033[1;33m[compile-launcher]\033[0m"
	bin/compile-launcher.sh

compile-config:
	@echo -e "\n\
	\033[1;33m[compile-config]\033[0m"
	bin/compile-config.sh

compile-all: compile-launcher compile-config

app: compile-all
	@echo -e "\n\
	\033[1;33m[app]\033[0m"
	bin/app.sh

test: app
	@echo -e "\n\
	\033[1;33m[test]\033[0m"
	bin/test.sh

.PHONY: tests
