#! /bin/bash

if [ ! -r updateSite.zip ]; then
	echo "No update site!"
	exit 1
fi

scp updateSite.zip dhovemey@cs.ycp.edu: && ssh cs.ycp.edu 'cd public_html && mkdir -p simpleMarmosetUploader && cd simpleMarmosetUploader && unzip -o ~/updateSite.zip'
