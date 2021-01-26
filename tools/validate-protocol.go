package main

import (
	"encoding/json"
	"log"
	"os"
)

type Protocol struct {
	DocVersion string `json:"doc_version"`
	Version    struct {
		Name    string
		Version string
		Branch  string
		Commit  string
	}
	Info    string
	Types   map[string]map[string]*Type
	Actions map[string]map[string]*Action
}

type Type struct {
	Fields     map[string]*DataType
	Request    bool `json:"-"`
	Doc        string
	Deprecated bool
}

type DataType struct {
	List    bool
	Type    string
	Version string
	Doc     string
	Example string
}

type Action struct {
	FnName        string
	Request       string
	RequestFields map[string]*DataType
	Response      string
	Doc           string
	Deprecated    bool
}

var protocol Protocol

func main() {
	err := json.NewDecoder(os.Stdin).Decode(&protocol)
	if err != nil {
		log.Fatal(err, "\nError parsing stdin")
	}
	pass := checkRequestResponseTypesExist()
	pass = checkMissingCriticalFields() && pass
	if !pass {
		log.Println("did not pass")
		os.Exit(1)
	}
}

func checkMissingCriticalFields() bool {
	pass := true

	if protocol.DocVersion == "" {
		log.Println("[MissingCriticalFields] root doc_version field is empty")
		pass = false
	}

	if len(protocol.Actions) == 0 {
		log.Println("[MissingCriticalFields] actions list is empty")
		pass = false
	} else {
		for version, actions := range protocol.Actions {
			if len(actions) == 0 {
				log.Println("[MissingCriticalFields] .actions." + version + " is empty")
				pass = false
			}
		}
	}

	if len(protocol.Types) == 0 {
		log.Println("[MissingCriticalFields] actions list is empty")
		pass = false
	} else {
		for version, types := range protocol.Types {
			if len(types) == 0 {
				log.Println("[MissingCriticalFields] .types." + version + " is empty")
				pass = false
			}
		}
	}
	return pass
}

// validates that all response types exist in the specified version
func checkRequestResponseTypesExist() bool {
	pass := true
	for version, actions := range protocol.Actions {
		for t, action := range actions {
			if _, ok := protocol.Types[version][action.Request]; !ok {
				pass = false
				log.Println("[MissingRequestType] request", t, version, "has request type", action.Request, "but no such type exists (is it referencing another version?)")
			}
			if action.Response != "" {
				if _, ok := protocol.Types[version][action.Response]; !ok {
					pass = false
					log.Println("[MissingResponseType] request", t, version, "has response type", action.Response, "but no such type exists (is it referencing another version?)")
				}
			}
		}
	}
	return pass
}
