package: clean
	git checkout main # to make sure mkinstaller is up-to-date
	ksh mkinstaller

clean:
	rm -rf *.dmg docs *runtime target
