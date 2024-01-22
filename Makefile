help:
	@bin/help.sh

clean:
	@bin/clean.sh

compile-launcher:
	@bin/compile-launcher.sh

compile-configurator:
	@bin/compile-configurator.sh

compile-all: compile-launcher compile-configurator

app: compile-all
	@bin/app.sh

test: app
	@bin/test.sh

.PHONY: tests
