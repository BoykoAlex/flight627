/*******************************************************************************
 * @license
 * Copyright (c) 2013 VMware, Inc. All Rights Reserved.
 * THIS FILE IS PROVIDED UNDER THE TERMS OF THE ECLIPSE PUBLIC LICENSE
 * ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION OF THIS FILE
 * CONSTITUTES RECIPIENTS ACCEPTANCE OF THE AGREEMENT.
 * You can obtain a current copy of the Eclipse Public License from
 * http://www.opensource.org/licenses/eclipse-1.0.php
 *
 * Contributors:
 *   Kris De Volder
 ******************************************************************************/

var ghRepoFs = require('../../plugable-fs/github-fs/github-repo-fs');

var token = require('../../plugable-fs/github-fs/secret').token;

if (!token) {
	return; // skip these tests if we don't have a github token to use.
}

var nodeCache = require(
	'../../plugable-fs/github-fs/rest-node-manager'
).configure({ limit: 20});
var fs = ghRepoFs.configure({
	token: token,
	owner: 'kdvolder',
	repo: 'playground',
	cache: nodeCache
});

var scriptedFs = require('../../plugable-fs/scripted-fs').configure(fs);

var fswalk = require('../../plugable-fs/utils/fswalk').configure(scriptedFs).fswalk;
var toCompareString = require('../../plugable-fs/utils/strings').toCompareString;

exports.readRoot = function (test) {
	fs.readdir('/', function (err, names) {
		if (err) {
			test.fail(""+err);
			if (err.stack) {
				console.log(err.stack);
			}
		} else {
			names.sort();
			test.equals(toCompareString(names), toCompareString([
				'.scripted','README.md','subdir'
			]));

			console.dir(fs.forTesting.rootNode);
		}
		test.done();
	});
};

exports.readSubdir = function (test) {
	fs.readdir('/subdir', function (err, names) {
		test.equals(toCompareString(names), toCompareString([
			'a-file.js'
		]));
		test.done();
	});
};

//Walk the tree and collect all the paths. See if we get them all.
exports.walk = function (test) {
	var collect = []; // Collect all the file paths in here
	fswalk('/',
		function (path) {
			collect.push(path);
		},
		function () {
			collect.sort();
			test.equals(toCompareString(collect), toCompareString([
				"/.scripted",
				"/README.md",
				"/subdir/a-file.js"
			]));
			test.done();
		}
	);
};

var README_TEXT =
	'playground\n==========\n\n' +
	'This is just a testing repo to play with.\n\n' +
	'Put some stuff in here to test github-fs.\n';

exports.readFileUTF8 = function (test) {
	fs.readFile('/README.md', 'utf8', function (err, text) {
		test.equals(text,
			README_TEXT
		);
		test.done();
	});
};

//exports.readFileBuffer = function (test) {
//	fs.readFile('/README.md', function (err, buffer) {
//		test.ok(buffer instanceof Buffer);
//		test.equals(buffer.toString('utf8'),
//			README_TEXT
//		);
//		test.done();
//	});
//};
