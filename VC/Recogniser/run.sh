#!/bin/sh
for i in *.vc
do
	echo $i:
	b=`basename "$i" .vc`
	java VC.vc $i > $b.output
	diff $b.output $b.sol
done