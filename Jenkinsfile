pipeline {
    agent any
    environment {
        DEPLOY_COMPOSE_FILE = 'deploy-compose.yml'
        APP_HEALTH_URL = 'http://localhost:8081/actuator/health'
    }

    options {
        skipDefaultCheckout(true)
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Clean Workspace') {
            steps {
                deleteDir()
            }
        }
    
        stage('Checkout') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/feature/my-changes"]],
                    userRemoteConfigs: [[
                        name: 'origin',
                        url: 'https://github.com/amanikandan79/ecomart-service-tw'
                    ]]
                ])
            }
        }

    stage('Debug SCM') {
            steps {
                sh '''
                    pwd
                    git branch
                    git log --oneline -5
                    git remote -v
                    ls -la
                '''
            }
        }
        
        stage('Environment Check') {
            steps {
                sh '''
                    whoami
                    pwd
                    java -version
                    echo JAVA_HOME=$JAVA_HOME
                    ./gradlew --version
                '''
            }
    }
        stage('Build') {
            steps {
                sh './gradlew clean assemble --no-daemon'
            }
        }

        stage('Unit Test') {
            steps {
                sh './gradlew test --no-daemon'
            }
        }

        stage('Deploy') {
            steps {
                archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
                sh '''
                    docker compose -f "${DEPLOY_COMPOSE_FILE}" down --remove-orphans || true
                    docker compose -f "${DEPLOY_COMPOSE_FILE}" up -d --build
                '''
            }
        }

        stage('Actuator Health Check') {
            steps {
                sh '''
                    for i in $(seq 1 30); do
                      if curl --silent --show-error --max-time 5 "${APP_HEALTH_URL}" | grep -q '"status":"UP"'; then
                        echo "Actuator health is UP"
                        exit 0
                      fi
                      sleep 2
                    done
                    echo "Actuator health check failed"
                    exit 1
                '''
            }
        }

        stage('Functional Test') {
            steps {
                sh './gradlew functionalTest --no-daemon'
            }
        }
    }

    post {
        always {
            sh 'docker compose -f "${DEPLOY_COMPOSE_FILE}" logs --no-color > build/deploy-compose.log || true'
            archiveArtifacts artifacts: 'build/reports/tests/test/**,build/reports/tests/functionalTest/**,build/deploy-compose.log', allowEmptyArchive: true
            junit testResults: 'build/test-results/test/*.xml,build/test-results/functionalTest/*.xml', allowEmptyResults: true
            script {
                def unitReportUrl = "${env.BUILD_URL}artifact/build/reports/tests/test/index.html"
                def functionalReportUrl = "${env.BUILD_URL}artifact/build/reports/tests/functionalTest/index.html"
                emailext(
                    recipientProviders: [
                        [$class: 'DevelopersRecipientProvider'],
                        [$class: 'RequesterRecipientProvider']
                    ],
                    subject: "[${env.JOB_NAME}] Build #${env.BUILD_NUMBER} - ${currentBuild.currentResult}",
                    body: """Build Result: ${currentBuild.currentResult}

Unit Test Report: ${unitReportUrl}
Functional Test Report: ${functionalReportUrl}

Build URL: ${env.BUILD_URL}
"""
                )
            }
            sh 'docker compose -f "${DEPLOY_COMPOSE_FILE}" down --remove-orphans || true'
        }
    }
}
