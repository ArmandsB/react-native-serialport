// types/index.d.ts
import type { Spec } from '../specs/NativeRNSerialport';

// === Native module with helpers ===
declare const RNSerialport: Spec & {
  intArrayToUtf16: (arr: number[]) => string;
  hexToUtf16: (hex: string) => string;
};

export interface IDevice {
  name: string;
  vendorId: number;
  productId: number;
}

export type Devices = Array<IDevice> | null;

export interface IOnReadData {
  payload: string | Array<number>
}
export interface IOnError {
  status: boolean;
  errorCode: number;
  errorMessage: string;
  exceptionErrorMessage?: string;
}
export interface IOnServiceStarted {
  deviceAttached: boolean
}

interface DefinitionsStatic {
  DATA_BITS: {
    DATA_BITS_5: number
    DATA_BITS_6: number;
    DATA_BITS_7: number;
    DATA_BITS_8: number;
  };
  STOP_BITS: {
    STOP_BITS_1: number;
    STOP_BITS_15: number;
    STOP_BITS_2: number;
  };
  PARITIES: {
    PARITY_NONE: number;
    PARITY_ODD: number;
    PARITY_EVEN: number;
    PARITY_MARK: number;
    PARITY_SPACE: number;
  };
  FLOW_CONTROLS: {
    FLOW_CONTROL_OFF: number;
    FLOW_CONTROL_RTS_CTS: number;
    FLOW_CONTROL_DSR_DTR: number;
    FLOW_CONTROL_XON_XOFF: number;
  };
  RETURNED_DATA_TYPES: {
    INTARRAY: number;
    HEXSTRING: number;
  };
  DRIVER_TYPES: {
    AUTO: string,
    CDC: string,
    CH34x: string,
    CP210x: string,
    FTDI: string,
    PL2303: string
  };
}
export var definitions: DefinitionsStatic;

interface ActionsStatic {
  ON_SERVICE_STARTED: string,
  ON_SERVICE_STOPPED: string,
  ON_DEVICE_ATTACHED: string,
  ON_DEVICE_DETACHED: string,
  ON_ERROR: string,
  ON_CONNECTED: string,
  ON_DISCONNECTED: string,
  ON_READ_DATA: string
}
export var actions: ActionsStatic;

export type DataBits = 5 | 6 | 7 | 8;
export type StopBits = 1 | 2 | 3;
export type Parities = 0 | 1 | 2 | 3 | 4;
export type FlowControls = 0 | 1 | 2 | 3;
export type ReturnedDataTypes = 1 | 2;
export type Drivers = "AUTO" | "cdc" | "ch34x" | "cp210x" | "ftdi" | "pl2303";

// === Default export ===
export { RNSerialport };