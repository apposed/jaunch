help:
	@bin/help.sh

clean:
	@bin/clean.sh

compile-launcher:
	@bin/compile-launcher.sh

compile-config:
	@bin/compile-config.sh

compile-all: compile-launcher compile-config

app: compile-all
	@bin/app.sh

test: app
	@bin/test.sh

.PHONY: tests
