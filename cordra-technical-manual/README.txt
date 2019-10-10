Required software:

* pip
    * sudo easy_install pip
        OR
    * Download bootstrap file from https://bootstrap.pypa.io/get-pip.py
    * sudo python get-pip.py
* Sphinx
    * sudo pip install Sphinx==1.7.2 --ignore-installed six
    * The "--ignore-installed six" flag is only needed if you are using the version of Python that comes with MacOS.
      See https://github.com/pypa/pip/issues/3165
* recommonmark
    * sudo pip install --upgrade recommonmark
* LaTex for building PDFs
    * https://tug.org/mactex/
    * I installed Basic TEX, and then had to install components using the following commands:
        - sudo tlmgr update --self
        - sudo tlmgr install titlesec framed threeparttable wrapfig multirow collection-fontsrecommended latexmk fncycha fncychap tabulary varwidth capt-of needspace xcolor lato inconsolata fontaxes
    * May need to restart terminal after installing to get commands working.

To build, run one of these two commands:
    * make html
    * make latexpdf

To clear build directory, run:
    * make clean
