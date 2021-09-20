package main

import "fmt"

func checkErrorsExist() (response checkOutputs) {
	for version, actions := range protocol.Actions {
		for t, action := range actions {
			for _, e := range action.Errors {
				_, ok := protocol.Types[version][e.Name]
				if !ok {
					m := checkOutput{id: "ErrorsExist", String: fmt.Sprintf("request %s.%s has error %s but no such type exists (is it referencing another version?)", version, t, e.Name)}
					response.failures = append(response.failures, m)
				}
			}
		}
	}
	return
}
