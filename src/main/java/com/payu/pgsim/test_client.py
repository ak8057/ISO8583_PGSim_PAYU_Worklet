import socket
import struct
import random
from datetime import datetime

HOST = "localhost"
PORT = 9000


# =========================
# UTILITIES
# =========================

def now_datetime():
    return datetime.now().strftime("%m%d%H%M%S")


def generate_stan():
    return f"{random.randint(0, 999999):06d}"


def generate_rrn():
    return f"{random.randint(0, 999999999999):012d}"


# =========================
# ISO BUILDER (SIMPLE)
# =========================

def build_0100(pan, amount):
    """
    Builds a basic 0100 message
    Fields: 2,3,4,11
    """

    mti = "0100"

    # bitmap: fields 2,3,4,11 → 7020000000000000
    bitmap = "7020000000000000"

    f2 = f"{len(pan):02d}{pan}"          # LLVAR PAN
    f3 = "000000"
    f4 = f"{amount:012d}"
    f11 = generate_stan()

    return mti + bitmap + f2 + f3 + f4 + f11


def build_0800(network_code="001"):
    mti = "0800"

    # Correct bitmap for fields 7, 11, 70
    bitmap = "82200000000000000400000000000000"

    f7 = now_datetime()     # 10 digits
    f11 = generate_stan()   # 6 digits
    f70 = network_code      # 3 digits

    return mti + bitmap + f7 + f11 + f70


# =========================
# TCP SEND
# =========================

def send_iso(msg, name="TEST"):
    print(f"\n==== {name} ====")

    data = msg.encode()

    # 2-byte length header
    length = struct.pack(">H", len(data))

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((HOST, PORT))
        s.sendall(length + data)

        try:
            resp = s.recv(4096)

            if resp:
                resp_body = resp[2:]

                print("RAW HEX :", resp.hex().upper())
                print("RESPONSE:", resp_body.decode(errors="ignore"))
            else:
                print("EMPTY RESPONSE")

        except Exception as e:
            print("NO RESPONSE", e)


# =========================
# TEST SCENARIOS
# =========================

def test_valid():
    msg = build_0100("5123456789012345", 1000)
    send_iso(msg, "VALID 0100")


def test_medium_amount():
    msg = build_0100("5123456789012345", 20000)
    send_iso(msg, "RULE TEST (51)")


def test_high_amount():
    msg = build_0100("5123456789012345", 60000)
    send_iso(msg, "RULE TEST (05)")


def test_invalid_pan():
    msg = build_0100("123", 1000)
    send_iso(msg, "INVALID PAN")


def test_invalid_amount():
    mti = "0100"
    bitmap = "7020000000000000"

    pan = "5123456789012345"
    f2 = f"{len(pan):02d}{pan}"
    f3 = "000000"
    f4 = "ABCDEFGHIJKL"  # invalid
    f11 = generate_stan()

    msg = mti + bitmap + f2 + f3 + f4 + f11
    send_iso(msg, "INVALID AMOUNT")


def test_network():
    msg = build_0800("001")
    send_iso(msg, "NETWORK 0800")


def test_no_response():
    msg = "0110" + "0000000000000000"
    send_iso(msg, "NO RESPONSE TEST")


# =========================
# MAIN
# =========================

if __name__ == "__main__":

    test_valid()
    test_medium_amount()
    test_high_amount()

    test_invalid_pan()
    test_invalid_amount()

    test_network()
    test_no_response()