/*******************************************************************************
 * @license
 * Copyright (c) 2012 VMware, Inc. All Rights Reserved.
 * THIS FILE IS PROVIDED UNDER THE TERMS OF THE ECLIPSE PUBLIC LICENSE
 * ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION OF THIS FILE
 * CONSTITUTES RECIPIENTS ACCEPTANCE OF THE AGREEMENT.
 * You can obtain a current copy of the Eclipse Public License from
 * http://www.opensource.org/licenses/eclipse-1.0.php
 *
 * Contributors:
 *     Kris De Volder - initial API and implementation
 ******************************************************************************/

/*global exports require*/
var eachk = require('./callback').eachk;
var pathResolve = require('./path').pathResolve;
var getFileName = require('./path').getFileName;
var getDirectory = require('./path').getDirectory;
var or = require('./promises').or;
var orMap = require('./promises').orMap;
var when = require('when');

function configure(conf) {

	var ignoreName = conf.ignore || require('../utils/filesystem').ignore;
	var ignorePath = conf.ignorePath || function () { return false; };
	var listFiles = conf.listFiles;
	var isDirectory = conf.isDirectory;

	// Walk the FILESYSTEM
	//A walk function written in callback style. Calls the function f on each file (excluding directories)
	//The function f is a non-callbacky function.
	//After all files have been walked, then orginal callback function passed to the toplevel walk
	//call will be called.
	function fswalk(node, f, k, exit) {
		exit = exit || k; //Grabs the 'toplevel' k
		if (ignorePath(node)) {
			return k();
		}
		isDirectory(node, function(isDir) {
			if (isDir) {
				listFiles(node,
					function(names) {
						eachk(names,

						function(name, k) {
							if (ignoreName(name)) {
								k();
							} else {
								var file = pathResolve(node, name);
								fswalk(file, f, k, exit);
							}
						},
						k);
					},

					function(err) {
						//ignore error and proceed.
						k();
					}
				);
			} else {
				var abort = f(node); // The f function ain't callback style.
				if (abort) {
					exit();
				} else {
					k();
				}
			}
		});
	}

	/**
	 * A version of fswalk where the function 'f' is callbacky as well.
	 * This is for use cases where the work that needs to be done on
	 * each file may also need to read stuff from the file system (or do
	 * other kinds of work that need a callbacky programming style).
	 * <p>
	 * Just like in the regular fswalk, the function f can return a boolean
	 * value of true to indicate that it wants to abort the walk.
	 * However since the function is 'callbacky' the boolean must be
	 * passed to the callback instead.
	 * <p>
	 * This function is written in 'continuation passing style'. This means
	 * that the the entire state of the computation is always represented by
	 * any of the 'k' functions passed around. Thus it is possible for the function
	 * 'f' to pause the search, simply by refraining from calling it's k
	 * and store it somewhere instead. To resume the search it just needs to
	 * call the stored function.
	 */
	function asynchWalk(node, f, k, exit) {
		exit = exit || k; //Grabs the 'toplevel' k
		if (ignorePath(node)) {
			return k();
		}
		isDirectory(node, function(isDir) {
			if (isDir) {
				listFiles(node,

				function(names) {
					eachk(names,
						function(name, k) {
							if (ignoreName(name)) {
								k();
							} else {
								var file = pathResolve(node, name);
								asynchWalk(file, f, k, exit);
							}
						},
						k
					);
				},

				function(err) {
					//ignore error and proceed.
					k();
				});
			} else {
				f(node, function(abort) {
					if (abort) {
						exit();
					} else {
						k();
					}
				});
			}
		});
	}

	/**
	 * Walk filesystem starting from given path looking for a file that meets
	 * some interesting property, possibly to extract some information from this file.
	 *
	 * Files are visited starting from the current directory visiting all files in
	 * this directory, then files in the parent directory and so on.
	 * When a file is found that contains what we are looking for then the walk aborts.
	 *
	 * We use promises to indicate whether or not the 'interesting result' was found.
	 * The f function should return a promise which is resolved when result is found,
	 * and rejected if result is not found.
	 *
	 * @return Promise
	 */
	function parentSearch(path, f) {
		return or(
			function () {
				return orMap(listFiles(path), function (name) {
					var child = pathResolve(path, name);
					return f(child);
				});
			},
			function () {
				var parent = getDirectory(path);
				if (parent) {
					return parentSearch(parent, f);
				} else {
					return when.reject('No more parents');
				}
			}
		);
	}

	return {
		fswalk: function (path, f, k) {
			fswalk(path, f, k);
		},
		asynchWalk: function (path, f, k) {
			asynchWalk(path, f, k);
		},
		parentSearch: parentSearch
	};

}

exports.configure = configure;
