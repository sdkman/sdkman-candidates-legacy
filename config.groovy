sdkmanVersion = 'CI_BUILD_NUMBER'
environments {
	local {
		sdkmanService = 'http://localhost:8080'
	}
	production {
		sdkmanService = 'https://api.sdkman.io/1'
	}
}
