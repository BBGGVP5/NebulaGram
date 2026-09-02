//go:build !cgo

// This stub keeps `go build ./...` working on a machine with no C toolchain.
// The real binding lives in main_cgo.go.
package main

func main() {}
