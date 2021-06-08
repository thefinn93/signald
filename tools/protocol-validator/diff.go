package main

import (
	"encoding/json"
	"fmt"
	"net/http"

	aurora "github.com/logrusorgru/aurora/v3"
)

func checkDiff() (response checkOutput, err error) {
	resp, err := http.Get("https://signald.org/protocol.json")
	if err != nil {
		return response, err
	}
	defer resp.Body.Close()
	var current Protocol
	err = json.NewDecoder(resp.Body).Decode(&current)
	if err != nil {
		return
	}

	// check for additions
	for version, actions := range protocol.Actions {
		if _, ok := current.Actions[version]; !ok {
			// new version
			fmt.Println(aurora.Bold(aurora.Green("New action version: " + version)))
		}
		for name := range actions {
			if _, ok := current.Actions[version][name]; !ok {
				// new action
				fmt.Println(aurora.Bold(aurora.Green("new action: " + version + "." + name)))
			}
		}
	}

	for version, types := range protocol.Types {
		if _, ok := current.Types[version]; !ok {
			// new version
			fmt.Println(aurora.Bold(aurora.Green("New version: " + version)))
		}
		for typeName, t := range types {
			if _, ok := current.Types[version][typeName]; !ok {
				// new action
				fmt.Println(aurora.Bold(aurora.Green("new type: " + version + "." + typeName)))
			}
			if current.Types[version][typeName].Deprecated != t.Deprecated {
				fmt.Println(aurora.Blue(version + "." + typeName + " has changed deprecated status"))
			}
			if current.Types[version][typeName].Doc != t.Doc {
				fmt.Println(aurora.Blue(version + "." + typeName + " has changed its doc string"))
			}
			for fieldName := range t.Fields {
				field, ok := current.Types[version][typeName].Fields[fieldName]
				if !ok {
					fmt.Println(aurora.Bold(aurora.Green("new field in " + version + "." + typeName + ": " + fieldName)))
					r := checkTypeFieldCasing(version, typeName, fieldName)
					response.failures = append(response.failures, r.failures...)
					response.warnings = append(response.warnings, r.warnings...)
				} else {
					if field.Type != current.Types[version][typeName].Fields[fieldName].Type {
						response.failures = append(response.failures, version+"."+typeName+" field "+fieldName+" changed types")
					}
					if field.List != current.Types[version][typeName].Fields[fieldName].List {
						response.failures = append(response.failures, version+"."+typeName+" field "+fieldName+" changed list state")
					}
					if field.Doc != current.Types[version][typeName].Fields[fieldName].Doc {
						fmt.Println(aurora.Blue(version + "." + typeName + " field " + fieldName + " changed it's doc string"))
					}
					if field.Example != current.Types[version][typeName].Fields[fieldName].Example {
						fmt.Println(aurora.Blue(version + "." + typeName + " field " + fieldName + " changed it's example string"))
					}
				}
			}
		}
	}

	// check for removals
	for version, actions := range current.Actions {
		if _, ok := protocol.Actions[version]; !ok {
			// new version
			fmt.Println(aurora.Bold(aurora.Red("removed action version: " + version)))
		}
		for name := range actions {
			if _, ok := protocol.Actions[version][name]; !ok {
				// new action
				fmt.Println(aurora.Bold(aurora.Red("removed action: " + version + "." + name)))
			}
		}
	}

	for version, types := range current.Types {
		if _, ok := protocol.Types[version]; !ok {
			// new version
			response.failures = append(response.failures, "removed version: "+version)
		}
		for typeName, t := range types {
			if _, ok := protocol.Types[version][typeName]; !ok {
				// new action
				response.failures = append(response.failures, "removed type: "+version+"."+typeName)
			}
			for fieldName := range t.Fields {
				_, ok := protocol.Types[version][typeName].Fields[fieldName]
				if !ok {
					response.failures = append(response.failures, "field in "+version+"."+typeName+" removed: "+fieldName)
				}
			}
		}
	}
	return
}
