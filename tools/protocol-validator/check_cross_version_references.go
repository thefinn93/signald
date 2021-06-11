package main

func checkCrossVersionReferences(version, t, field string, d DataType) (response checkOutput) {
	if d.Version != "" && d.Version != version {
		response.failures = append(response.failures, version+"."+t+" field "+field+" has a data type from a different version")
	}
	return
}
