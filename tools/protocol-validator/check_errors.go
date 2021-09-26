package main

import "fmt"

func checkErrorsExist() (response checkOutputs) {
	for version, actions := range protocol.Actions {
		for t, action := range actions {
			for _, e := range action.Errors {
				errorType, ok := protocol.Types[version][e.Name]
				if !ok {
					m := checkOutput{id: "ErrorsExist", String: fmt.Sprintf("request %s.%s has error %s but no such type exists (is it referencing another version?)", version, t, e.Name)}
					response.failures = append(response.failures, m)
					continue
				}

				if !errorType.Error {
				    response.failures = append(response.failures, checkOutput{id: "NonErrorInErrorList", String: fmt.Sprintf("%s.%s throw error %s, but %s is not an error. Make sure your exception extends ExceptionWrapper", version, t, e.Name, e.Name)})
				}
			}
		}
	}
	return
}
