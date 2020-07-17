#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

library "knime-pipeline@$BN"

properties([
    pipelineTriggers([
        upstream('knime-json/' + env.BRANCH_NAME.replaceAll('/', '%2F'))
    ]),
    parameters(workflowTests.getConfigurationsAsParameters()),
    buildDiscarder(logRotator(numToKeepStr: '5')),
    disableConcurrentBuilds()
])

try {
    knimetools.defaultTychoBuild('org.knime.update.salesforce')

	// Specifying configurations is optional. If omitted, the default configurations will be used
	// (see jenkins-pipeline-libraries/vars/workflowTests.groovy)
 	// def testConfigurations = [
	// 	 "ubuntu18.04 && python-3",
	//	 "windows && python-3"
	// ]

//	workflowTests.runTests(
//		// The name of the feature that pulls in all required dependencies for running workflow tests.
//		// You can also provide a list here but separating it into one feature makes it clearer and allows
//		// pulling in independant plug-ins, too.
//		"org.knime.features.ap-repository-template.testing.feature.group",
//		// with or without assertions
//		false,
//		// a list of upstream jobs to look for when getting dependencies
//		["knime-core", "knime-shared", "knime-tp"],
//		// optional list of test configurations
//		testConfigurations
//	)
//
//	stage('Sonarqube analysis') {
//		env.lastStage = env.STAGE_NAME
//		// passing the test configuration is optional but must be done when they are
//		// used above in the workflow tests
//		workflowTests.runSonar(testConfigurations)
//	}
} catch (ex) {
    currentBuild.result = 'FAILURE'
    throw ex
} finally {
    notifications.notifyBuild(currentBuild.result);
}
/* vim: set shiftwidth=4 expandtab smarttab: */
