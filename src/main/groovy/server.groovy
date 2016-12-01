/*
 *  Copyright 2012 Marco Vermeulen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

import groovy.json.JsonOutput
import groovy.text.SimpleTemplateEngine
import org.vertx.groovy.core.http.RouteMatcher

import static java.net.URLEncoder.encode

final SDKMAN_VERSION = '@SDKMAN_VERSION@'
final COLUMN_LENGTH = 15
final UNIVERSAL_PLATFORM = "UNIVERSAL"

//
// datasource configuration
//

def config = [
    address: (System.getenv('SDKMAN_DB_ADDRESS') ?: 'mongo-persistor'),
    db_name: (System.getenv('SDKMAN_DB_NAME') ?: 'sdkman'),
    host: System.getenv('SDKMAN_DB_HOST'),
    port: System.getenv('SDKMAN_DB_PORT')?.toInteger(),
    username: System.getenv('SDKMAN_DB_USERNAME'),
    password: System.getenv('SDKMAN_DB_PASSWORD')
]

container.deployModule 'vertx.mongo-persistor-v1.2', config

def templateEngine = new SimpleTemplateEngine()
def sdkmanBase = "sdkman"

def listVersionsTemplateFile = "${sdkmanBase}/list_versions.gtpl" as File
def listVersionsTemplate = templateEngine.createTemplate(listVersionsTemplateFile)

def listCandidatesTemplateFile = "${sdkmanBase}/list_candidates.gtpl" as File
def listCandidatesTemplate = templateEngine.createTemplate(listCandidatesTemplateFile)

def installScriptText = new File("${sdkmanBase}/install.sh").text

def selfupdateScriptText = new File("${sdkmanBase}/selfupdate.sh").text

//
// route matcher implementations
//

def rm = new RouteMatcher()

rm.get("/") { req ->
    def cmd = [action:"find", collection:"application", matcher:[:], keys:[cliVersion:1]]
    vertx.eventBus.send("mongo-persistor", cmd){ msg ->
        def sdkmanCliVersion = msg.body.results.cliVersion.first()
        addPlainTextHeader req
        req.response.end installScriptText.replace("<SDKMAN_CLI_VERSION>", sdkmanCliVersion)
    }
}

rm.get("/selfupdate") { req ->
    boolean beta = Boolean.valueOf(req.params['beta'] as String) ?: false
    def cmd = [action:"find", collection:"application", matcher:[:], keys:[cliVersion:1, betaCliVersion:1]]
    vertx.eventBus.send("mongo-persistor", cmd){ msg ->
        String sdkmanCliVersion = msg.body.results.cliVersion.first()
        String sdkmanBetaCliVersion = msg.body.results.betaCliVersion.first()
        addPlainTextHeader req
        req.response.end selfupdateScriptText.replace("<SDKMAN_CLI_VERSION>", (beta ? sdkmanBetaCliVersion : sdkmanCliVersion))
    }
}

rm.get("/robots.txt") { req ->
    addPlainTextHeader req
    req.response.sendFile("${sdkmanBase}/robots.txt")
}

rm.get("/alive") { req ->
    def cmd = [action:"find", collection:"application", matcher:[alive:"OK"]]
    vertx.eventBus.send("mongo-persistor", cmd){ msg ->
        def alive = msg.body.results.alive.first()
        addPlainTextHeader req
        req.response.end alive
    }
}

rm.get("/res") { req ->
	def version = req.params['version'] as String
	def purpose = req.params['purpose'] as String
	def baseUrl = "https://bintray.com/artifact/download"
	def folders = "sdkman/generic"

	def cmd = [action:"find", collection:"application", matcher:[:], keys:[cliVersion:1]]
	vertx.eventBus.send("mongo-persistor", cmd){ msg ->
		def sdkmanCliVersion = version ?: msg.body.results.cliVersion.first() as String

		def artifact = "sdkman-cli-${encode(sdkmanCliVersion, "UTF-8")}.zip"
		def zipUrl = "$baseUrl/$folders/$artifact"

		log purpose, 'sdkman', sdkmanCliVersion, req

		req.response.putHeader("Content-Type", "application/zip")
		req.response.putHeader("Location", zipUrl)
		req.response.statusCode = 302
		req.response.end()
	}
}

rm.get("/candidates") { req ->
	def cmd = [action:"find", collection:"candidates", matcher:[distribution: UNIVERSAL_PLATFORM], keys:[candidate:1]]
	vertx.eventBus.send("mongo-persistor", cmd){ msg ->
		def candidates = msg.body.results.collect(new TreeSet()) { it.candidate }
		addPlainTextHeader req
		req.response.end candidates.join(',')
	}
}

candidateList = "Candidate List initialising. Please try again..."
TreeMap candidates = [:]
rm.get("/candidates/list") { req ->
    def cmd = [action    : "find",
               collection: "candidates",
               matcher   : [:],
               keys      : [candidate  : 1,
                            default    : 1,
                            description: 1,
                            websiteUrl : 1,
                            name       : 1]]
	if(candidates.isEmpty()){
		vertx.eventBus.send("mongo-persistor", cmd) { msg ->
			msg.body.results.each {
				candidates.put(
						it.candidate,
						[
								header     : header(it.name, it.default, it.websiteUrl),
								description: paragraph(it.description),
								footer     : footer(it.candidate)
						]
				)
			}
			def binding = [candidates: candidates]
			def template = listCandidatesTemplate.make(binding)
			candidateList = template.toString()
		}
	}
	addPlainTextHeader req
	req.response.end candidateList
}

PARAGRAPH_WIDTH = 80

def header(String name, String version, String websiteUrl) {
    int padding = PARAGRAPH_WIDTH - websiteUrl.length()
    "${name} (${version})".padRight(padding, " ") + websiteUrl
}

def paragraph(String text) {
	def words = text.tokenize(" ")
	def lines = format(words)
	lines.join("\n")
}

def footer(String candidate) {
    "\$ sdk install $candidate".padLeft(PARAGRAPH_WIDTH)
}

def format(List words) {
    def lineWords = [], lineLength = 0, idx = 0
    for (word in words) {
        idx++
        lineLength += word.size() + 1
        lineWords << word
        if (lineLength > PARAGRAPH_WIDTH) {
            def lineText = lineWords.take(lineWords.size() - 1).join(" ")
            def remainingWords = [word] + words.drop(idx)
            return [lineText] + format(remainingWords)
        }
    }
    [words.join(" ")]
}

rm.get("/candidates/:candidate") { req ->
	def candidate = req.params['candidate']
	def cmd = [action:"find", collection:"versions", matcher:[candidate:candidate, platform: UNIVERSAL_PLATFORM], keys:["version":1]]
	vertx.eventBus.send("mongo-persistor", cmd){ msg ->
		def response
		if(msg.body.results){
			def versions = msg.body.results.collect(new TreeSet()) { it.version }
			response = versions.join(',')

		} else {
			response = "invalid"
		}

		addPlainTextHeader req
		req.response.end response
	}
}

rm.get("/candidates/:candidate/default") { req ->
	def candidate = req.params['candidate']
	def cmd = [action:"find", collection:"candidates", matcher:[candidate:candidate, distribution: UNIVERSAL_PLATFORM], keys:["default":1]]
	vertx.eventBus.send("mongo-persistor", cmd){ msg ->
		addPlainTextHeader req
		def defaultVersion = msg.body.results.default
		req.response.end (defaultVersion ?: "")
	}
}

rm.get("/candidates/:candidate/details") { req ->
	def candidate = req.params['candidate']
	def cmd = [
			action:"find",
			collection:"candidates",
			matcher:[candidate:candidate, distribution: UNIVERSAL_PLATFORM],
			keys:[
                    "candidate":1,
					"default":1,
                    "templateUrl":1,
                    "broadcast":1,
                    "release":1]]
	vertx.eventBus.send("mongo-persistor", cmd){ msg ->
		req.response.putHeader("Content-Type", "application/json")
		req.response.end JsonOutput.toJson(
				candidate: msg.body.results.candidate[0],
				default: msg.body.results.default[0],
				templateUrl: msg.body.results.templateUrl[0],
				broadcast: msg.body.results.broadcast[0],
				release: msg.body.results.release[0])
	}
}

rm.get("/candidates/:candidate/list") { req ->
    def candidate = req.params['candidate']
    def uname = req.params['platform']
    def currentVersion = req.params['current'] ?: ''
    def installedVersions = req.params['installed'] ? req.params['installed'].tokenize(',') : []

    def candidateCmd = [
            action    : "find",
            collection: "candidates",
            matcher   : [candidate: candidate],
            keys      : ["distribution": 1]]

    vertx.eventBus.send("mongo-persistor", candidateCmd) { candidateMsg ->
        def distribution = (candidateMsg.body.results.distribution.first() == "PLATFORM_SPECIFIC") ? determinePlatform(uname) : UNIVERSAL_PLATFORM
        def versionCmd = [
                action    : "find",
                collection: "versions",
                matcher   : [candidate: candidate, platform: distribution],
                keys      : ["version": 1],
                sort      : ["version": -1]]
        vertx.eventBus.send("mongo-persistor", versionCmd) { versionMsg ->
            def availableVersions = versionMsg.body.results.collect { it.version }

            def combinedVersions = combineVersions(availableVersions, installedVersions)
            def localVersions = determineLocalVersions(availableVersions, installedVersions)

            def content = prepareVersionListView(combinedVersions, currentVersion, installedVersions, localVersions, COLUMN_LENGTH)
            def binding = [candidate: candidate, content: content]
            def template = listVersionsTemplate.make(binding)

            addPlainTextHeader req
            req.response.end template.toString()
        }
    }
}

private determinePlatform(uname) {
	switch (uname) {
		case "Darwin":
			return "MAC_OSX"
		case "Linux":
			return "LINUX"
		case "FreeBSD":
			return "FREE_BSD"
		case "SunOS":
			return "SUN_OS"
		case "MINGW32":
			return "WINDOWS_32"
		default:
			return "WINDOWS_64"
	}
}

private prepareVersionListView(combined, current, installed, local, colLength){
    def builder = new StringBuilder()
    for (i in (0..(colLength-1))){
        def versionColumn1 = prepareVersion(combined[i], current, installed, local)
        def versionColumn2 = prepareVersion(combined[i+(colLength*1)], current, installed, local)
        def versionColumn3 = prepareVersion(combined[i+(colLength*2)], current, installed, local)
        def versionColumn4 = prepareVersion(combined[i+(colLength*3)], current, installed, local)
        builder << "${pad(versionColumn1)} ${pad(versionColumn2)} ${pad(versionColumn3)} ${pad(versionColumn4)}\n"
    }
    builder.toString()
}

private prepareVersion(version, current, installed, local){
    def isCurrent = (current == version)
    def isInstalled = installed.contains(version)
    def isLocalOnly = local.contains(version)
    decorateVersion(version, isCurrent, isInstalled, isLocalOnly)
}

private decorateVersion(version, isCurrent, isInstalled, isLocalOnly) {
    " ${markCurrent(isCurrent)} ${markStatus(isInstalled, isLocalOnly)} ${version ?: ''}"
}

private pad(col, width=20) {
    (col ?: "").take(width).padRight(width)
}

private markCurrent(isCurrent){
    isCurrent ? '>' : ' '
}

private markStatus(isInstalled, isLocalOnly){
    if(isInstalled && isLocalOnly) '+'
    else if(isInstalled) '*'
    else ' '
}

private determineLocalVersions(available, installed){
    installed.findAll { ! available.contains(it) }
}

private combineVersions(available, installed){
    def combined = [] as TreeSet
    combined.addAll installed
    combined.addAll available
    combined.toList().reverse()
}

def validationHandler = { req ->
	def candidate = req.params['candidate']
	def version = req.params['version']
	def cmd = [action:"find", collection:"versions", matcher:[candidate:candidate, version:version, platform: UNIVERSAL_PLATFORM]]
	vertx.eventBus.send("mongo-persistor", cmd){ msg ->
		addPlainTextHeader req
		if(msg.body.results) {
			req.response.statusCode = 200
			req.response.end 'valid'
		} else {
			req.response.statusCode = 404
			req.response.end 'invalid'
		}
	}
}

rm.get("/candidates/:candidate/:version/validate", validationHandler)
rm.get("/candidates/:candidate/:version", validationHandler)

def downloadHandler = { req ->
	def candidate = req.params['candidate']
	def version = req.params['version']

	log 'install', candidate, version, req

	def cmd = [action:"find",
			   collection:"versions",
			   matcher:[candidate:candidate,
						version:version,
						platform: UNIVERSAL_PLATFORM],
			   keys:["url":1]]
	vertx.eventBus.send("mongo-persistor", cmd){ msg ->
		req.response.headers['Location'] = msg.body.results.url.first()
		req.response.statusCode = 302
		req.response.end()
	}
}

rm.get("/candidates/:candidate/:version/download", downloadHandler)
rm.get("/download/:candidate/:version", downloadHandler)

def versionHandler = { String field ->
	{ req ->
		def cmd = [action: "find", collection: "application", matcher: [:], keys: [(field): 1]]
		vertx.eventBus.send("mongo-persistor", cmd) { msg ->
			String cliVersion = msg.body.results."$field".first()
			addPlainTextHeader req
			req.response.end cliVersion
		}
	}
}

rm.get("/app/stable", versionHandler('cliVersion'))
rm.get("/app/beta", versionHandler('betaCliVersion'))
rm.get("/app/version", versionHandler('cliVersion'))
rm.get("/app/cliversion", versionHandler('cliVersion'))

rm.get("/api/version") { req ->
    addPlainTextHeader req
    req.response.end SDKMAN_VERSION
}

//
// private methods
//

private addPlainTextHeader(req){
	req.response.putHeader("Content-Type", "text/plain")
}

private log(command, candidate, version, req){
	def date = new Date()
	def host = req.headers['X-Real-IP']
	def agent = req.headers['user-agent']
	def platform = req.params['platform']

	def document = [
		command:command,
		candidate:candidate,
		version:version,
		host:host,
		agent:agent,
		platform:platform,
		date:date
	]

	def cmd = [action:'save', collection:'audit', document:document]

	vertx.eventBus.send 'mongo-persistor', cmd
}

//
// startup server
//
def port = System.getenv('PORT') ?: 8080
def host = System.getenv('PORT') ? '0.0.0.0' : 'localhost'
println "Starting vertx on $host:$port"
vertx.createHttpServer().requestHandler(rm.asClosure()).listen(port as int, host)
