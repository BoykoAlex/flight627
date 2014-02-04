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

var nodefs = require('fs');
var pathResolve = require('../utils/path').pathResolve;

var secretFile = pathResolve(__dirname, 'secret.json');

var secret = {}; //No secret info by default.

try {
	secret = JSON.parse(nodefs.readFileSync(secretFile));
} catch (err) {
	console.log(
		"WARNING: No github secret token available, github-fs won't work and its tests will be skipped\n"+
		"Create a github api token and store in the 'token' propery in file '"+secretFile+"'"
	);
}

console.log('secret = '+JSON.stringify(secret));

module.exports = secret;