export PROJECT_TOKEN=<INSERT-YOUR-PROJECT_TOKEN-HERE>
if [ "$1" == "remote" ]
then
  export BITBAR_KEY=<INSERT-YOUR-BITBAR_KEY-HERE>
  export HUB_URL=<INSERT-YOUR-HUB_URL-HERE>
  mvn clean test -Dsuite-xml=BitBarScript.xml
else
  mvn clean test -Dsuite-xml=DemoScript.xml
fi