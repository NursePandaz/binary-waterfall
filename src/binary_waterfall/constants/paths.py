import os
import sys
IS_EXE = bool(getattr(sys, "frozen", False))
PATH = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
