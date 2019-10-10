#!/bin/bash

make clean
make singlehtml
cd _build/singlehtml/
pandoc -o ../manual.docx index.html
cd -
