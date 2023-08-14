@Library('sharedlibrary') _
pipeline {
    agent any
    tools {
        gradle "7.4.2"
        jdk "JDK8"
        dockerTool "docker"
        maven "maven-3.8.5"
    }
    stages {
        stage("Build") {
            steps {
                script{
                    build()
                }
            }
        }   
        /*stage("Scan") {
            steps {
                script {
                    sonarScan()
                }
            }
        } */
        stage("publish") {
            steps {
                script {
                    publish("gradle")
                }
            }
        }
        stage("standalone-deploy") {
        	when {
				anyOf {
					branch 'development'
					branch 'main'
				}
			}
            steps {
                script {
                    standaloneDeployment("172.21.0.65",["9180:9180"])
                }
            }
        }
        stage("deployment-k8s") {
			when { branch 'deploy-to-cluster' }
            steps {
                script {
                    helm()
                }
            }
        }
    }
}

