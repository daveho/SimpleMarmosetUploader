#! /bin/bash

(rm -rf updateSite.zip && \
	cd SimpleMarmosetUploaderUpdateSite && \
	zip -9r ../updateSite.zip features plugins site.xml)
