#  Copyright Contributors to the OpenCue Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.


"""Outline configuration"""


from __future__ import print_function
from __future__ import division
from __future__ import absolute_import

from builtins import str

# pylint: disable=wrong-import-position
from future import standard_library
standard_library.install_aliases()
# pylint: enable=wrong-import-position

import getpass
import os
import pathlib
import tempfile

import six

from six.moves import configparser
if six.PY2:
    ConfigParser = configparser.SafeConfigParser
else:
    ConfigParser = configparser.ConfigParser


__all__ = ["config"]
__file_path__ = pathlib.Path(__file__)

PYOUTLINE_ROOT_DIR = __file_path__.parent.parent
DEFAULT_USER_DIR = pathlib.Path(tempfile.gettempdir()) / 'opencue' / 'outline' / getpass.getuser()

config = ConfigParser()

default_config_paths = [__file_path__.parent.parent.parent / 'etc' / 'outline.cfg',
                        __file_path__.parent.parent / 'etc' / 'outline.cfg']
default_config_path = None
for default_config_path in default_config_paths:
    if default_config_path.exists():
        break

config.read(os.environ.get("OL_CONFIG", str(default_config_path)))

# Add defaults to the config,if they were not specified.
if not config.get('outline', 'home'):
    config.set('outline', 'home', str(PYOUTLINE_ROOT_DIR))

if not config.get('outline', 'user_dir'):
    config.set('outline', 'user_dir', str(DEFAULT_USER_DIR))
