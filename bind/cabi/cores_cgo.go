//go:build cgo

package main

import "github.com/nebulagram/nebulagram/bind/cores"

func init() { cores.Register() }
