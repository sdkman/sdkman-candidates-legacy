vertxVersion = '1.3.1.final'
sdkmanVersion = 'CI_BUILD_NUMBER'
environments {
	local {
		sdkmanService = 'http://localhost:8080'
	}
	production {
		sdkmanService = 'https://api.sdkman.io/2'
	}
}
