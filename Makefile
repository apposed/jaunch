help:
	@bin/help.sh

clean:
	@bin/clean.sh

compile-launcher:
	@bin/compile-launcher.sh

compile-configurator:
	@bin/compile-configurator.sh

compile-all: compile-launcher compile-configurator

dist: compile-all
	@bin/dist.sh

pack: dist
	@bin/pack.sh

demo: dist
	@bin/demo.sh

test: demo
	@bin/test.sh

.PHONY: tests
