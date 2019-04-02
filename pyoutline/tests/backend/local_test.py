#!/usr/bin/env python

#  Copyright (c) 2018 Sony Pictures Imageworks Inc.
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


import os
import mock
import unittest

import outline
import outline.backend.local


SCRIPTS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'scripts'))


class BuildCommandTest(unittest.TestCase):
    def setUp(self):
        path = os.path.join(SCRIPTS_DIR, 'shell.outline')
        self.ol = outline.load_outline(path)
        self.launcher = outline.cuerun.OutlineLauncher(self.ol)
        self.layer = self.ol.get_layer('cmd')

    def testBuildShellCommand(self):
        frameNum = 47

        generatedCmd = outline.backend.local.build_command(self.ol, self.layer, frameNum)

        self.assertEqual(
            [
                '/wrappers/local_wrap_frame', '', 'testing', 'default', '/bin/pycuerun',
                '%s/shell.outline -e  %d-cmd' % (SCRIPTS_DIR, frameNum), ' -v latest',
                ' -r ', '-D'
            ], generatedCmd)


class SerializeTest(unittest.TestCase):
    def setUp(self):
        path = os.path.join(SCRIPTS_DIR, 'shell.outline')
        self.ol = outline.load_outline(path)
        self.launcher = outline.cuerun.OutlineLauncher(self.ol)

    def testSerialize(self):
        self.assertEqual(
            outline.backend.local.Dispatcher,
            outline.backend.local.serialize(self.launcher).__class__)

    def testSerializeSimple(self):
        self.assertEqual(
            outline.backend.local.Dispatcher,
            outline.backend.local.serialize_simple(self.launcher).__class__)


class BuildFrameRangeTest(unittest.TestCase):
    def testBuildFrameRange(self):
        self.assertEqual([3, 4, 5, 6, 7, 8, 9], outline.backend.local.build_frame_range('3-9', 1))

    def testBuildChunkedFrameRange(self):
        self.assertEqual([3, 7], outline.backend.local.build_frame_range('3-9', 4))

    def testBuildLargeChunkedFrameRange(self):
        self.assertEqual([3], outline.backend.local.build_frame_range('3-9', 87))


class DispatcherTest(unittest.TestCase):
    @mock.patch('subprocess.call')
    def testDispatch(self, subprocessCallMock):
        path = os.path.join(SCRIPTS_DIR, 'shell.outline')
        ol = outline.load_outline(path)
        launcher = outline.cuerun.OutlineLauncher(ol)
        subprocessCallMock.return_value = 0

        outline.backend.local.launch(launcher)
