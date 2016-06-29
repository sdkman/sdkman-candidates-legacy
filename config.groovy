vertxVersion = '1.3.1.final'
sdkmanVersion = 'CI_BUILD_NUMBER'
environments {
	local {
		sdkmanService = 'http://localhost:8080'
		sdkmanBroadcastService = 'http://localhost:8080'
		sdkmanBrokerService = 'http://localhost:8080'
	}
	production {
		sdkmanService = 'http://api.sdkman.io'
		sdkmanBroadcastService = 'http://cast.sdkman.io'
		sdkmanBrokerService = 'http://broker.sdkman.io'
	}
}
