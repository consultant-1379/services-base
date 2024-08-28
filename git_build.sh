#!/bin/bash

if [ "$2" == "" ]; then
    	echo usage: $0 \<Branch\> \<RState\>
    	exit -1
else
	versionProperties=install/version.properties
	theDate=\#$(date +"%c")
	module=$1
	branch=$2
	workspace=$3
fi

function getProductNumber {
        product=`cat $workspace/build.cfg | grep $module | grep $branch | awk -F " " '{print $3}'`
}


function setRstate {

        revision=`cat $workspace/build.cfg | grep $module | grep $branch | awk -F " " '{print $4}'`

       	if git tag | grep $product-$revision; then
	        rstate=`git tag | grep $revision | tail -1 | sed s/.*-// | perl -nle 'sub nxt{$_=shift;$l=length$_;sprintf"%0${l}d",++$_}print $1.nxt($2) if/^(.*?)(\d+$)/';`
        else
                ammendment_level=01
                rstate=$revision$ammendment_level
        fi
	echo "Building R-State:$rstate"

}


function nexusDeploy {
	#RepoURL=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/assure-releases
	RepoURL=https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/content/repositories/assure-releases

	GroupId=com.ericsson.eniq.events
	ArtifactId=$module
	
	echo "****"	
	echo "Deploying the jar /$module-1.0.jar as ${ArtifactId}${rstate}.jar to Nexus...."
        mv target/$module-1.0.jar target/${ArtifactId}.jar
	echo "****"	

  	mvn deploy:deploy-file \
	        	-Durl=${RepoURL} \
		        -DrepositoryId=assure-releases \
		        -Dpackaging=jar \
		        -DgroupId=${GroupId} \
		        -Dversion=${rstate} \
		        -DartifactId=${ArtifactId} \
		        -Dfile=target/${ArtifactId}.jar
		         
	 			

}

getProductNumber
setRstate
git checkout $branch
git pull origin $branch

#add maven command here
mvn clean install -Dmaven.test.skip=true -P noPMD -U

nexusDeploy

rsp=$?

if [ $rsp == 0 ]; then

  git tag $product-$rstate
  git pull
  git push --tag origin $branch

fi

exit $rsp
