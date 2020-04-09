mvn exec:java@NPowER -Dexec.args='-peer=data/3.txt -models=data/1.txt:data/2.txt -allScores'
mvn exec:java@NPowER -Dexec.args='-peer=data/4.txt -models=data/1.txt:data/2.txt -allScores'
mvn exec:java@NPowER -Dexec.args='-peer=data/1.txt -models=data/3.txt:data/4.txt -allScores'
mvn exec:java@NPowER -Dexec.args='-peer=data/2.txt -models=data/3.txt:data/4.txt -allScores'