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
#  Zurich, Switzerland, March 2021.
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
DIR=$(dirname $(readlink -f $0 || realpath $0))

# Change to path
echo "$DIR"

# Configuration
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-java8-20.1.0/Contents/Home

# Compilation
mvn clean package dependency:copy-dependencies -Dmaven.test.skip=true

