import requests
import subprocess
import re

def main():
    totalInsts = len(instList)
    #print("{} msas to check...".format(totalMSAs))

    for i, inst in enumerate(instList):
        print("****** Institution: {} ({} of {}) ****".format(inst, i, totalInsts))
        instUri = s3BaseUrl + inst + "/"
        msaList = lsList(instUri)

        for j, msa in enumerate(msaList):
            msaUri = instUri + msa + "/"
            fromLocation = msaUri + "A4W.txt"
            toLocation = msaUri + "A4.txt"
            command = "aws s3 mv {} {}".format(fromLocation, toLocation)
            output = subprocess.getoutput(command)
            print(output)

        #for report in renames:
        #    fromLocation = s3BaseUrl + inst + "/" + report + ".txt"
        #    toLocation = s3BaseUrl + inst + "/" + renames[report] + ".txt"
        #    command = "aws s3 mv {} {}".format(fromLocation, toLocation)
        #    output = subprocess.getoutput(command)
        #    print(output)


renames = {
"A4W": "A4"
}

def cleanOutput(line):
    ln = line.lstrip().strip("/")
    return re.sub(r"^PRE ","",ln)

def lsList(uri):
    output = subprocess.getoutput("aws s3 ls " + uri)
    ls = []
    for line in output.split('\n'):
        cleaned = cleanOutput(line)
        ls.append(cleaned)
    return ls



s3BaseUrl = "s3://cfpb-hmda-public/prod/reports/disclosure/2017/"
instList = lsList(s3BaseUrl)

main()

