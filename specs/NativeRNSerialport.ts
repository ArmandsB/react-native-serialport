import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

// === Enums (must match Definitions.java) ===
export enum ReturnedDataTypes {
  INTARRAY = 0,
  HEXSTRING = 1,
}

export enum DataBits {
  DATA_BITS_5 = 5,
  DATA_BITS_6 = 6,
  DATA_BITS_7 = 7,
  DATA_BITS_8 = 8,
}

export enum StopBits {
  STOP_BITS_1 = 1,
  STOP_BITS_2 = 2,
}

export enum Parities {
  PARITY_NONE = 0,
  PARITY_ODD = 1,
  PARITY_EVEN = 2,
  PARITY_MARK = 3,
  PARITY_SPACE = 4,
}

export enum FlowControls {
  FLOW_CONTROL_OFF = 0,
  FLOW_CONTROL_ON = 1,
}

export enum Drivers {
  AUTO = 'AUTO',
  FTDI = 'ftdi',
  CP210X = 'cp210x',
  PL2303 = 'pl2303',
  CH34X = 'ch34x',
  CDC = 'cdc',
}

// === Types ===
export interface Device {
  name: string;
  vendorId: number;
  productId: number;
}

export type Devices = Device[];

// === Spec ===
export interface Spec extends TurboModule {
  // Service Control
  startUsbService(): void;
  stopUsbService(): void;

  // Status
  isOpen(): Promise<boolean>;
  isServiceStarted(): Promise<boolean>;
  isSupported(deviceName: string): Promise<boolean>;

  // Device List
  getDeviceList(): Promise<Devices>;

  // Connection
  connectDevice(deviceName: string, baudRate: number): void;
  disconnect(): void;

  // Write Methods
  writeString(data: string): void;
  writeBase64(data: string): void;
  writeHexString(data: string): void;
  writeBytes(data: number[]): void; // byte[] in Java

  // Setters
  setReturnedDataType(type: ReturnedDataTypes): void;
  setInterface(iFace: number): void;
  setDataBit(bit: DataBits): void;
  setStopBit(bit: StopBits): void;
  setParity(parity: Parities): void;
  setFlowControl(control: FlowControls): void;
  setAutoConnectBaudRate(baudRate: number): void;
  setAutoConnect(status: boolean): void;
  setDriver(driver: Drivers): void;
  setReadBufferSize(bufferSize: number): void;

  // Defaults
  loadDefaultConnectionSetting(): void;

  // Utilities
  intArrayToUtf16(intArray: number[]): string;
  hexToUtf16(hex: string): string;
}

// === Export ===
export default TurboModuleRegistry.getEnforcing<Spec>('RNSerialport');