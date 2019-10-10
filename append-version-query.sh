#!/bin/bash
mkdir -p build/tmp/html-with-version/js-test && \
cp src/main/webapp/*.html build/tmp/html-with-version && \
cp src/main/webapp/js-test/*.html build/tmp/html-with-version/js-test && \
VERSION=$(git rev-parse --short=12 HEAD || hg id -i || echo +) && \
if [[ "${VERSION: -1}" == "+" ]]; then VERSION=$(date +%FT%T%z); fi && \
sed -i.backup -E \
 -e 's#(<link rel="stylesheet" href="css/[^?"]*)"#\1?v='$VERSION'"#' \
 -e 's#(<script src="js/[^?"]*)"#\1?v='$VERSION'"#' \
 build/tmp/html-with-version/*.html && \
rm build/tmp/html-with-version/*.html.backup && \
sed -i.backup -E \
 -e 's#(<script src="[^:?"]*)"#\1?v='$VERSION'"#' \
 build/tmp/html-with-version/js-test/qunit.html && \
rm build/tmp/html-with-version/js-test/qunit.html.backup
