def sbtBuilder = "gcr.io/${env.GCLOUD_PROJECT}/jenkins/sbt-builder:latest"
def gcloudDocker = "gcr.io/${env.GCLOUD_PROJECT}/jenkins/gcloud-docker:latest"

pipeline {
  agent none
  stages {
    stage('Checkout & Build & Push') {
      steps {
        node('cloudBuilder') {
          checkout scm
          sh """#!/bin/bash -ex
          mkdir -p ${HOME}/.ivy2/
          echo ${env.ARTIFACTORY_CREDS} | base64 --decode > ${HOME}/.ivy2/.credentials
          """
          withDockerContainer(sbtBuilder) {
            sh """#!/bin/bash -ex
            sbt assembly"""
          }
          withDockerContainer(gcloudDocker) {
            sh """#!/bin/bash -ex
            echo ${env.GCLOUD_SERVICE_KEY} | base64 --decode > ${HOME}/gcloud-service-key.json
            gcloud auth activate-service-account --key-file ${HOME}/gcloud-service-key.json
            gcloud config set project ${env.GCLOUD_PROJECT}
            gcloud docker -a
            docker tag red/cerberus gcr.io/${env.GCLOUD_PROJECT}/cerberus:${env.GIT_BRANCH}_${env.GIT_COMMIT}
            docker tag red/cerberus gcr.io/${env.GCLOUD_PROJECT}/cerberus:${env.GIT_BRANCH}_latest
            docker tag red/cerberus gcr.io/${env.GCLOUD_PROJECT}/cerberus:latest
            gcloud docker -- push gcr.io/${env.GCLOUD_PROJECT}/cerberus"""
          }
        }
      }
    }
  }
}