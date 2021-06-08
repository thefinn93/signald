package main

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
