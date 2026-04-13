#!/bin/bash

# 
#  Copyright (C) Pathmate Technologies AG - All Rights Reserved
# 
#  Unauthorized copying of this file, via any medium is strictly prohibited.
# 
#  Proprietary and confidential.
# 
#  Written by Andreas Filler (CTO) in behalf of Pathmate Technologies AG.
# 
#  Zurich, Switzerland, April 2026.
# 
#  In case of questions contact us at contact@pathmate-technologies.com .
# 

# Basic setup
PLATFORM=$(uname -s)
if [ "$PLATFORM" = "Darwin" ]
	then
	export LC_CTYPE=C
	export LANG=C
	alias readlink=greadlink
fi
DIR=$(dirname "$(readlink -f "$0" || realpath "$0")")

# Change to path
echo $DIR

# Properties file
PROPS_FILE="${DIR}/version.properties"

if [ ! -f "${PROPS_FILE}" ]; then
	echo "ERROR: ${PROPS_FILE} not found." >&2
	exit 1
fi

# Read version
VERSION=$(grep "^version=" "${PROPS_FILE}" | cut -d'=' -f2)

if [ -z "$VERSION" ]; then
	echo "ERROR: 'version' not set in ${PROPS_FILE}." >&2
	exit 1
fi

echo "Setting version: $VERSION"

# Update all pom.xml files with the Pathmate version
find "$DIR" -name "pom.xml" -print | while read -r POM; do
	if grep -q "pathmate" "$POM"; then
		sed -i '' "s|[0-9][0-9.]*-pathmate-[0-9]*|${VERSION}|g" "$POM"
		echo "  Updated: $POM"
	fi
done

echo "Done."
