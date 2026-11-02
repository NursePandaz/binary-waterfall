def run():
    from .runtime import configure
    configure()
    import sys
    if "--self-test" in sys.argv:
        from .selftest import main
        main()
    else:
        from .core import run as launch
        launch()
