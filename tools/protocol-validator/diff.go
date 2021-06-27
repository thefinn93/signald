package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"

	aurora "github.com/logrusorgru/aurora/v3"
)

func checkDiff() (response checkOutput, err error) {
	resp, err := http.Get("https://signald.org/protocol.json")
	if err != nil {
		return
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
			c, ok := current.Types[version][typeName]
			if !ok {
				// new action
				fmt.Println(aurora.Bold(aurora.Green("new type: " + version + "." + typeName)))
				c = &Type{}
			} else {
				if c.Deprecated != t.Deprecated {
					fmt.Println(aurora.Blue(version + "." + typeName + " has changed deprecated status"))
					stringDiff(strconv.FormatBool(c.Deprecated), strconv.FormatBool(t.Deprecated))
				}
				if c.Doc != t.Doc {
					fmt.Println(aurora.Blue(version + "." + typeName + " has changed its doc string"))
					stringDiff(c.Doc, t.Doc)
				}
			}
			for fieldName, field := range t.Fields {
				currentField, ok := c.Fields[fieldName]
				if !ok {
					fmt.Println(aurora.Bold(aurora.Green("new field in " + version + "." + typeName + ": " + fieldName)))
					for _, fieldCheck := range fieldChecks {
						result := fieldCheck(version, typeName, fieldName, *field)
						response.failures = append(response.failures, result.failures...)
						response.warnings = append(response.warnings, result.warnings...)
					}
				} else {
					if field.Type != currentField.Type {
						response.failures = append(response.failures, version+"."+typeName+" field "+fieldName+" changed types")
						stringDiff(currentField.Type, field.Type)
					}
					if field.List != currentField.List {
						response.failures = append(response.failures, version+"."+typeName+" field "+fieldName+" changed list state")
						stringDiff(strconv.FormatBool(currentField.List), strconv.FormatBool(field.List))
					}
					if field.Doc != currentField.Doc {
						fmt.Println(aurora.Blue(version + "." + typeName + " field " + fieldName + " changed it's doc string"))
						stringDiff(currentField.Doc, field.Doc)
					}
					if field.Example != currentField.Example {
						fmt.Println(aurora.Blue(version + "." + typeName + " field " + fieldName + " changed it's example string"))
						stringDiff(currentField.Example, field.Example)
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
		v, ok := protocol.Types[version]
		if !ok {
			if version == "v0" {
				response.warnings = append(response.warnings, "Version "+version+". removed")
			} else {
				response.failures = append(response.failures, "Version "+version+". removed")
			}
		}
		for typeName, t := range types {
			oldType, ok := v[typeName]
			if !ok {
				if version == "v0" {
					response.warnings = append(response.warnings, "removed type: "+version+"."+typeName)
				} else {
					response.failures = append(response.failures, "removed type: "+version+"."+typeName)
				}

			}
			for fieldName := range t.Fields {
				_, ok = oldType.Fields[fieldName]
				if !ok {
					if version == "v0" {
						response.warnings = append(response.warnings, "field in "+version+"."+typeName+" removed: "+fieldName)
					} else {
						response.failures = append(response.failures, "field in "+version+"."+typeName+" removed: "+fieldName)
					}
					continue
				}
			}
		}
	}
	return
}

func stringDiff(old, new string) {
	fmt.Println(aurora.Red("- " + old))
	fmt.Println(aurora.Green("+ " + new))
}
